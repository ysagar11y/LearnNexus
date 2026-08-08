# LearnNexus

A multi-tenant SaaS learning management platform. One deployment serves many
organisations; each gets its own address, branding, people, courses, reports and
certificates, with data isolation enforced by the database rather than by
convention.

Built from the requirements in `docs/REQUIREMENTS.md`, on the stack that
document recommends: **Java 21 + Spring Boot 3, PostgreSQL, React 19 +
TypeScript**.

**Want to put this somewhere friends can reach it?** →
[`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) — a free, three-signup deploy
(Render + Neon + Cloudflare Pages), no credit card, ~15 minutes.

---

## Running it

Everything runs in Docker; no local JDK is required.

```bash
# 1. infrastructure — Postgres, Redis, MinIO, Mailpit
docker compose up -d
docker compose up minio-init          # creates the media bucket, once

# 2. the API (http://localhost:8081)
./mvnw.sh spring-boot:run             # first run also seeds demo data

# 3. the web app (http://localhost:5173)
cd frontend && npm install && npm run dev
```

Then open **http://localhost:5173/welcome** and enter a workspace address.

### Demo accounts

Password for every account: `Learn@2026`

| Workspace   | Address     | Sign in as              | Role                      |
|-------------|-------------|-------------------------|---------------------------|
| Acme Corp   | `acme`      | `priya@acme.test`       | Workspace admin           |
|             |             | `daniel@acme.test`      | Instructor + author       |
|             |             | `sara@acme.test`        | Manager                   |
|             |             | `arjun@acme.test`       | Learner                   |
| Northwind   | `northwind` | `helena@northwind.test` | Workspace admin           |
|             |             | `tom@northwind.test`    | Learner                   |
| Platform    | `platform`  | `ops@learnnexus.app`    | Platform super-admin      |

Sign into **acme** and then **northwind** to see the same product re-themed from
each tenant's own three colour dials — the fastest way to confirm that theming
is derived rather than hardcoded.

### Ports

`5433` Postgres · `6380` Redis · `9000/9001` MinIO · `8025` Mailpit (sent email)
· `8081` API · `5173` web

These avoid the defaults on purpose, so the stack can run alongside other
projects on the same machine.

---

## What is implemented

Everything in the source document's MVP, plus several Phase-2 items.

**Tenancy** — sub-domain, custom-domain and header resolution; per-tenant
branding, locale, quotas and feature flags; a platform console for provisioning,
resizing and suspending workspaces.

**Identity** — email/password with BCrypt (cost 12), rotating refresh tokens with
reuse detection, account lockout, invitation and reset flows, six roles, an
organisation hierarchy with manager-subtree visibility, and an append-only audit
trail.

**Learning** — courses with sections and lessons (video, PDF, HTML, audio, link,
quiz), a draft/review/published workflow, categories, tags and prerequisites; an
enrolment engine covering manual, self-service, department-rule and CSV-import
assignment with deadlines and seat limits; a player that resumes where the
learner stopped and tracks progress per lesson.

**Assessment** — quizzes and exams with five question types, question pools with
shuffling and sampling, time limits, attempt limits, negative marking,
auto-grading, and a grading queue for written answers.

**Credentials** — tenant-editable HTML certificate templates rendered to PDF,
each carrying a code anyone can verify without an account, plus expiry and
revocation.

**Reporting** — seven standard reports with CSV export, an admin dashboard, and
per-course analytics. Managers are automatically confined to their own part of
the organisation.

Not built: SSO/SCIM, SCORM runtime, payment capture, and the mobile app. The
schema and the module boundaries anticipate them; see `docs/ARCHITECTURE.md`.

---

## Layout

```
backend/           Spring Boot API — a modular monolith
  src/main/java/com/learnnexus/
    tenancy/       tenant resolution and the isolation mechanism
    security/      JWT, filters, method security
    auth/          sign-in, token rotation, password lifecycle
    iam/           users, roles, organisation hierarchy
    tenant/        settings, branding, feature flags
    catalog/       courses, sections, lessons, categories
    media/         presigned direct-to-storage uploads
    enrollment/    assignment, progress, the player
    assessment/    authoring, attempts, grading
    certificate/   issuing, PDF rendering, public verification
    reporting/     the report catalogue and dashboards
    platform/      cross-tenant super-admin console
  src/main/resources/db/migration/   Flyway
frontend/          React 19 + TypeScript + Vite
design-system/     the shared design system (tokens, components, UI kits)
docs/              architecture and the source requirements
```

The `design-system/` directory is the single source of truth for the visual
language. The web app references it by path rather than copying it, so the
preview cards, the UI kits and the running product cannot drift apart.

---

## Tests

```bash
./mvnw.sh test      # unit tests, no database
./mvnw.sh verify    # + integration tests against real PostgreSQL
cd frontend && npm run typecheck && npm run build
```

The integration suite starts PostgreSQL with Testcontainers. When Maven itself
is containerised — which is what `./mvnw.sh` does — Testcontainers cannot reach
the Docker socket on Docker Desktop, so point the suite at a database instead:

```bash
docker compose exec -T postgres psql -U learnnexus -d postgres \
  -c "CREATE DATABASE learnnexus_test;"

docker run --rm -v "$PWD/backend":/app -v learnnexus-m2:/root/.m2 -w /app \
  --network learnnexus_default \
  -e TEST_DB_URL=jdbc:postgresql://postgres:5432/learnnexus_test \
  maven:3.9-eclipse-temurin-21 mvn -B verify
```

`TenantIsolationIT` is the one to read first: it attempts eight real
cross-tenant accesses through the ordinary application code paths and asserts
that every one of them comes back empty.

---

## API

OpenAPI UI at **http://localhost:8081/docs**, schema at `/v3/api-docs`.

Every request is scoped to one tenant, resolved in this order:

1. the `X-Tenant` header — used by the SPA in local development, where there is
   no sub-domain to read;
2. an exact custom-domain match on the host (`learn.acme.com`);
3. a sub-domain of the configured root domain (`acme.learnnexus.app`).

An access token issued for one tenant is rejected when presented against
another, rather than being silently re-scoped.

---

## Configuration

Defaults in `backend/src/main/resources/application.yml` are development values.
Before deploying, set at minimum:

| Variable | Why |
|---|---|
| `JWT_SECRET` | 32+ bytes. The default is a placeholder and must not ship. |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | |
| `S3_*` | Real object storage; `S3_PUBLIC_ENDPOINT` must be the host browsers can reach. |
| `MAIL_*` | An SMTP relay that will actually deliver. |
| `TENANT_ROOT_DOMAIN` | The apex under which tenant sub-domains resolve. |
| `SEED_ENABLED=false` | Stops the demo seeder in any shared environment. |
