# Architecture

The decisions worth knowing about, and why they were made that way.

---

## 1. A modular monolith, not microservices

The source requirements sketch eight services behind an API gateway. That is the
right shape eventually; it is the wrong shape now. Enrolment, progress,
assessment and certificates are transactionally entangled — completing a lesson
recalculates progress, which may complete an enrolment, which issues a
certificate, which sends a notification. Split across services on day one, that
becomes a saga with compensating actions and a stream of "why is this learner's
progress wrong" bugs.

So: one deployable, with module boundaries drawn where the services eventually
go. Cross-module calls go through narrow interfaces (`CertificateIssuer` is the
clearest example — enrolment triggers certificate issuing without knowing
anything about PDF rendering). When a module needs to become a service, the seam
already exists.

---

## 2. Tenant isolation

This is the property the whole product rests on, so it is worth being precise
about what enforces it.

### The mechanism

Every business table carries `tenant_id`, and every tenant-scoped entity extends
`TenantScoped`, whose single field is annotated `@TenantId`. Hibernate then:

- appends `tenant_id = ?` to every generated select, update and delete;
- populates the column on insert from `CurrentTenantIdentifierResolver`.

The consequence that matters: **isolation does not depend on each repository
method remembering to filter.** A developer writing `findAll()` gets a scoped
query. To read across tenants you have to write native SQL, which is greppable
and reviewable.

### Failing closed

`TenantContext.tenantIdOrSentinel()` returns the nil UUID when no tenant is
resolved, never null and never a wildcard. A bug that loses the tenant context
produces *empty results*, not everyone's data. `TenantIsolationIT` asserts this
directly.

### The escape hatch

Two places legitimately read across tenants:

- `PlatformController` — the super-admin console, gated on `PLATFORM_ADMIN` at
  the security-config level, using `TenantAwareJdbc.unscoped()`.
- Certificate verification — a public endpoint that takes a 20-character random
  code as its credential and returns only non-identifying fields.

`TenantAwareJdbc` exists to make the *ordinary* case safe: it binds the current
tenant as the first parameter of every query and refuses to run SQL that does
not mention `tenant_id` at all. That check is a tripwire for the realistic
mistake, not a proof against a determined bypass — which is why the deliberate
bypass has its own method name.

### The ordering trap

Hibernate reads the tenant **when a session opens**, not when a statement runs.
Calling `TenantContext.runAs(...)` inside an already-open transaction therefore
changes nothing about the SQL. This bit the demo seeder: every insert carried
the sentinel tenant and failed the foreign key.

`TenantScopedExecutor` fixes it with `REQUIRES_NEW`, forcing a fresh session
after the tenant is set. Anything that switches tenant mid-process — the seeder,
tenant provisioning, scheduled sweeps — has to go through it.

### Defence in depth

Composite foreign keys include `tenant_id`, so a row cannot reference a parent
belonging to another tenant even if application code tried:

```sql
CONSTRAINT lessons_module_fk FOREIGN KEY (tenant_id, module_id)
    REFERENCES course_modules (tenant_id, id)
```

Row-level security would be a further layer. It is not enabled, because doing it
properly requires setting a session GUC on every pooled connection, and a
half-configured RLS policy is worse than none — it looks like protection while
being inert. The composite FKs and the discriminator are enforced today; RLS is
the documented next step.

---

## 3. Colour is derived, not authored

The design system resolves every colour from three numbers: brand hue, brand
chroma, accent hue. Those map 1:1 onto `tenant_branding` columns.

This works because **lightness is fixed per ramp step and only hue rotates**.
Contrast ratios therefore hold at every hue, and no tenant palette needs
hand-checking. A tenant admin moving a slider cannot produce an unreadable
product.

The server keeps its own copy of the three defaults (`config/DesignSystem`)
because it renders two surfaces the browser never sees — transactional email and
certificate PDFs — and those must match the app.

A literal hex value anywhere in a component is a bug: it will look wrong on
someone else's tenant.

---

## 4. Authentication

Short-lived access tokens (JWT, 30 min) plus long-lived opaque refresh tokens,
stored only as SHA-256 hashes.

Refresh tokens **rotate on every use** and carry a `family_id`. Presenting a
token that has already been rotated is evidence of theft, so the entire family is
revoked — signing out both the attacker and the legitimate user, who then
re-authenticates.

That revocation runs in its own transaction (`RefreshTokenGuard`). This is not
incidental: revoking and then throwing the rejection in one transaction rolls the
revocation back, leaving the stolen token working. `AuthenticationIT` caught
exactly that bug and now guards against its return.

Access tokens are held **in memory only** on the client. Persisting them would
widen the XSS blast radius for no benefit, since the refresh token already
survives a reload and is the one the server can revoke.

The client side has its own hazard, and it took two attempts to get right.
Reading the stored token *before* checking whether a refresh is already in
flight opens a race: a second caller captures the current token, is suspended
while the first refresh rotates it, then resumes and sends the stale one. The
server correctly reads that as theft. The token is now read inside the
single-flight promise, and the session-restore effect is guarded by a ref
because refreshing is a write and React deliberately double-invokes effects in
development.

**Known limitation.** Strict rotation has no grace window. If a refresh request
is sent but its response never arrives — a closed tab, a dropped connection —
the server has rotated the token while the browser still holds the old one, and
the next load is treated as a replay and signs the user out. The standard remedy
is to accept the immediately-preceding token for a few seconds and return the
replacement that was already issued, treating it as theft only outside that
window. That needs the rotated row to record its successor, and is deliberately
left undone rather than approximated: a half-built grace window is a hole in the
reuse detection, which is the only thing making stolen refresh tokens
survivable.

---

## 5. The report catalogue

Seven reports, each a named SQL statement with a typed column list, in one enum.
A single generic endpoint, one table component and one CSV exporter serve all of
them; adding a report is a data change, not a new controller/service/DTO triple.

Two details that are easy to get wrong:

- **Bind order varies per report.** A filter attached to a `LEFT JOIN` binds
  before the `WHERE` that carries the tenant. Each report declares its own slot
  sequence rather than trusting a shared convention, and `ReportService` refuses
  to run a statement that does not bind the tenant exactly once.
- **Manager scoping is server-side.** A manager asking for "the completion
  report" silently gets their own org-unit subtree. The client cannot widen it.

---

## 6. Things that bit, and what they taught

**Postgres cannot type a null bind used inside `lower()`.** The common
`(:query is null or lower(col) like ...)` idiom sends the null as `bytea` and
fails. Explicit HQL casts fix the string cases. For the audit search — four
optional filters of mixed types, where a `uuid` cast of a `bytea` null is
outright invalid — the fix was to stop generating the predicate at all and build
it with `Specification`. Dynamic predicates are the real answer; casts are a
patch that works until a type resists it.

**Short-answer questions stored their acceptable answers as options**, and the
attempt view was serialising options for every question type — handing learners
the answer key. Now only choice questions get an option list. Found by writing a
script that answered a quiz as a learner would.

**`Optional.of()` on a mapper that can return null.** `max(percentage)` over
zero attempts returns a row containing NULL; the row mapper correctly mapped it
to null; `Optional.of` threw. `queryOne` now uses `ofNullable`.

**Thymeleaf 3.1 made OGNL optional.** Spring-managed engines use SpringEL, so
nothing in a normal Boot app notices — until you build a standalone
`TemplateEngine`, which certificate rendering does because templates live in the
database rather than on the classpath.

---

## 7. Deliberate omissions

- **SSO / SCIM.** The role model and `users` schema accommodate it; the work is
  a SAML/OIDC relying party plus a provisioning endpoint.
- **SCORM runtime.** Packages can be stored and linked; running one needs an
  xAPI/SCORM API adapter and a launch frame.
- **Payments.** `subscriptions` and `invoices` exist and are populated;
  Razorpay/Stripe capture is not wired.
- **Row-level security.** See §2 — deliberately absent rather than half-done.
- **Distributed scheduling.** `ScheduledJobs` is single-node. Running more than
  one instance needs a lock (ShedLock over the existing Redis) or learners get
  duplicate reminder emails.
