# Source requirements

The specification this project was built from, captured from the planning
conversation and reformatted to plain Markdown. Kept in the repository so the
implementation can be checked against what was actually asked for.

`ARCHITECTURE.md` records where the implementation deviates, and why.

---

You’re essentially planning to build a **SaaS Learning Management System (LMS)** with **multi-tenant architecture**—similar to platforms used by universities, corporate training companies, coaching institutes, and edtech providers. Since you’re an SDE-II with backend experience, I’ll approach this like a **product + architecture requirements document** rather than a generic feature list.

I looked at leading LMS vendors that support multi-tenancy and enterprise deployments, including TalentLMS, Docebo, Moodle Workplace, Absorb LMS, LearnUpon, and Canvas LMS. Their public product pages consistently emphasize tenant isolation, branded portals, role-based access, course management, analytics, integrations, and enterprise governance. [TalentLMS](https://www.talentlms.com/) [Docebo](https://www.docebo.com/) [Moodle Workplace](https://moodle.com/solutions/workplace/) [Absorb LMS](https://www.absorblms.com/) [LearnUpon](https://www.learnupon.com/) [Canvas LMS](https://www.instructure.com/canvas)

# Industry benchmark: what “top-tier” LMS products provide

| Platform | Multi-tenant | Primary market |
|---|---|---|
| TalentLMS | ✔ | SMB & corporate training |
| Docebo | ✔ | Enterprise learning SaaS |
| Moodle Workplace | ✔ | Enterprise / education |
| Absorb LMS | ✔ | Enterprise training |
| LearnUpon | ✔ | Customer & partner training |
| Canvas LMS | ✔ | Higher education |

Common capabilities across these products: branded portals, isolated user/course data, hierarchical organizations, SSO, reporting, content standards (SCORM/xAPI), and API integrations.

---

# Product vision

**Goal:** One platform serving many organizations (“tenants”), where each organization gets its own branded learning portal, users, courses, reports, and billing while sharing the same application infrastructure.

Think:

- `acme.learnportal.com`
- `bharatpe.learnportal.com`
- `schoolxyz.learnportal.com`

Each tenant should feel like a separate application.

---

# Requirements elicitation from industry products

## 1. Tenant management (core SaaS requirement)

### Functional

- Create / suspend / delete tenant
- Custom domain mapping (`learn.acme.com`)
- Branding: logo, colors, favicon, email templates
- Time zone, locale, currency
- Tenant-level feature flags (e.g., assessments enabled)
- Tenant quotas: users, storage, API rate limits
- Tenant admin console

### Non-functional

- Strict data isolation
- No cross-tenant queries by default
- Tenant-aware caching and background jobs

**Must-have for MVP:** tenant ID propagation through every request.

---

## 2. Identity & access management

### Roles

| Role | Scope |
|---|---|
| Platform Super Admin | All tenants |
| Tenant Admin | Own tenant |
| Instructor | Assigned courses |
| Learner | Enrolled courses |
| Manager | Team reporting |
| Content Author | Content library |

### Enterprise requirements

- Email/password, OTP, social login
- SSO: SAML 2.0, OIDC, Azure AD, Google Workspace
- SCIM user provisioning
- MFA
- Password policy per tenant
- Session management and device logout
- Audit logs for all admin actions

Industry LMSs heavily market SSO and enterprise identity integration.

---

## 3. Organization hierarchy

Large customers need sub-organizations.

Example:

```
Acme Corp
 ├── Engineering
 │    ├── Backend
 │    └── Mobile
 ├── Sales
 └── HR
```

Requirements:

- Hierarchical org tree
- Department-based enrollment
- Manager visibility restricted to subtree
- Bulk user import by department

This is a major differentiator between simple LMSs and enterprise LMSs.

---

# Learning domain requirements

## 4. Course management

### Course types

- Self-paced
- Instructor-led virtual
- Instructor-led classroom
- Blended learning
- Learning path / certification track

### Course structure

- Course → module → lesson → activity
- Draft / review / published workflow
- Versioning
- Prerequisites
- Estimated duration
- Tags and categories
- Multi-language metadata

### Content formats

- Video
- PDF
- PPT
- HTML
- Audio
- SCORM 1.2 / 2004
- xAPI (Tin Can)
- External links

SCORM/xAPI support is expected in enterprise LMS evaluations.

---

## 5. Content authoring

Top products provide lightweight authoring even if they integrate external tools.

- Rich text editor
- Video upload and transcoding
- Quiz builder
- Assignment builder
- Question banks
- Reusable content blocks
- Content approval workflow
- AI-assisted content generation (future phase)

---

## 6. Enrollment engine

### Enrollment modes

- Manual
- Self-enrollment
- Invite link
- Department rule
- CSV bulk import
- API enrollment
- Learning-path auto enrollment

### Business rules

- Seat limits
- Waitlist
- Enrollment expiry
- Mandatory training deadlines
- Recertification reminders

---

# Assessment requirements

## 7. Assessments & exams

- MCQ, multi-select, true/false
- Subjective questions
- Programming/coding assessments (future)
- Question pools and randomization
- Negative marking
- Timed tests
- Attempt limits
- Passing criteria
- Proctoring integration hooks
- Auto-grading + manual grading

---

## 8. Certificates & compliance

Critical for corporate learning.

- Certificate templates
- Dynamic fields (name, score, date)
- Verification URL / QR code
- Certificate expiry
- Renewal workflows
- Compliance dashboards
- Training completion evidence retention

---

# Learner experience

## 9. Learner portal

- Personal dashboard
- Continue learning
- Progress tracking
- Calendar of live sessions
- Certificates wallet
- Bookmarks / notes
- Discussion participation
- Mobile responsive UI
- Offline download (mobile app later)

---

## 10. Social & engagement

Modern LMSs increasingly include engagement features.

- Discussion forums
- Course Q&A
- Announcements
- Mentions and notifications
- Badges and gamification
- Leaderboards
- Peer review activities

---

# Instructor requirements

## 11. Instructor workspace

- Learner roster
- Attendance tracking
- Gradebook
- Assignment review
- Live session management (Zoom/Teams/Meet integration)
- Session recordings
- Instructor analytics

---

# Analytics & reporting

## 12. Reporting module

### Standard reports

- Course completion
- Learner progress
- Assessment scores
- Compliance overdue
- Instructor activity
- Enrollment trends
- Learning hours

### Enterprise analytics

- Scheduled reports
- CSV/XLSX export
- Email delivery
- API access
- Dashboard builder
- BI warehouse connector

Reporting is one of the most frequently highlighted enterprise LMS capabilities.

---

# Communication requirements

## 13. Notification system

Channels:

- Email
- In-app
- Push (future)
- SMS/WhatsApp (optional)

Triggers:

- Enrollment
- Course assigned
- Deadline reminder
- Session reminder
- Certificate issued
- Password reset

Need tenant-specific branding and SMTP settings.

---

# Integration requirements

## 14. Integrations

### HR / identity

- Workday
- SAP SuccessFactors
- BambooHR
- Azure AD
- Okta
- Google Workspace

### Meeting tools

- Zoom
- Microsoft Teams
- Google Meet

### Content

- Vimeo
- YouTube
- SharePoint
- S3-compatible storage

### Developer

- REST API
- Webhooks
- API keys / OAuth2

Public APIs are expected by enterprise buyers.

---

# SaaS billing requirements

## 15. Subscription & billing

- Plans: Free / Pro / Enterprise
- Per active user pricing
- Storage add-ons
- Invoice generation
- GST support (India)
- Razorpay / Stripe integration
- Usage metering
- Trial management
- Payment failure handling

---

# Administration requirements

## 16. Platform operations

Super-admin capabilities:

- Tenant search
- Impersonate tenant admin
- Global announcements
- Feature rollout
- Usage monitoring
- Abuse detection
- Support ticket linkage
- Backup/restore controls

---

# Security & compliance (critical)

## 17. Security requirements

- Tenant isolation tests
- RBAC enforcement
- Encryption in transit (TLS)
- Encryption at rest
- Signed URLs for content
- OWASP Top 10 protection
- Rate limiting
- WAF support
- Audit trails immutable
- Data retention policies
- GDPR-style export/delete APIs
- Indian DPDP Act readiness

For enterprise deals, auditability is often as important as features.

---

# Non-functional requirements

## 18. Scalability targets

Example target:

- **Tenants** — 10,000+
- **Concurrent learners** — 100,000+
- **Video streaming** — CDN backed
- **API p95 latency** — <300 ms
- **Availability** — 99.9%
- **RPO** — 15 min
- **RTO** — 1 hour

---

# Recommended multi-tenant architecture

## 19. Data isolation model

| Model | Pros | Cons |
|---|---|---|
| Shared DB, shared schema | Cheapest | Weak isolation |
| Shared DB, separate schema | Good balance | Schema management complexity |
| Separate DB per tenant | Strong isolation | Operational cost |
| Hybrid | Best enterprise flexibility | Most complex |

### My recommendation for your scale

**Hybrid model**

- SMB tenants → shared database with `tenant_id`
- Enterprise tenants → dedicated database

This is the pattern many SaaS platforms evolve toward.

---

## 20. Suggested system architecture

```
Client (Web / Mobile)
        |
   CDN + WAF
        |
   API Gateway
        |
 Tenant Resolver (subdomain/custom domain)
        |
------------------------------
| Auth Service               |
| Tenant Service             |
| Course Service             |
| Enrollment Service         |
| Assessment Service         |
| Certificate Service        |
| Notification Service       |
| Reporting Service          |
------------------------------
        |
 Postgres / MySQL
 Redis
 Kafka / RabbitMQ
 S3 + CloudFront
 Elasticsearch / OpenSearch
```

For an MVP, a **modular monolith** is faster than microservices.

---

# Domain model (minimum entities)

```
Tenant
OrganizationUnit
User
Role
Membership
Course
CourseVersion
Module
Lesson
Enrollment
Progress
Assessment
Question
Attempt
Certificate
Notification
AuditLog
Subscription
Invoice
```

Every business table except global reference tables should contain `tenant_id`.

---

# API requirements

## 21. Public API examples

```
POST /api/v1/tenants
POST /api/v1/users
POST /api/v1/courses
POST /api/v1/courses/{id}/enroll
GET  /api/v1/reports/completions
POST /api/v1/webhooks
```

Requirements:

- OAuth2 client credentials
- Tenant-scoped API keys
- Idempotency keys for imports
- Pagination/filtering/sorting

---

# Search requirements

## 22. Search & discovery

- Full-text course search
- Facets (category, skill, language)
- Synonyms
- Auto-suggest
- Tenant-scoped indexing

Use OpenSearch/Elasticsearch once data grows.

---

# Mobile requirements (phase 2)

- Native or Flutter app
- Offline course download
- Push notifications
- Biometric login
- Video resume playback

---

# AI features (competitive roadmap)

- Course recommendation engine
- Skill-gap analysis
- AI quiz generation
- AI transcript & subtitles
- AI tutor / chatbot
- Learning summary generation

Docebo and newer LMS vendors are investing heavily here.

---

# MVP vs Phase roadmap

## MVP (3–4 months)

**MVP**
**Must launch with**
- Multi-tenant auth & RBAC
- Tenant branding
- User management
- Course management
- Video/PDF lessons
- Enrollments
- Progress tracking
- Quizzes
- Certificates
- Email notifications
- Basic reports
- REST API

## Phase 2

SSO, org hierarchy, SCORM, live classes, advanced analytics, billing.

## Phase 3

Mobile apps, AI features, marketplace, dedicated tenant databases.

---

# Technology stack suggestion (fits your background)

| Layer | Recommendation |
|---|---|
| Backend | Java 21 + Spring Boot 3 |
| Security | Spring Security + Keycloak/Auth0 |
| Database | PostgreSQL |
| Cache | Redis |
| Async | Kafka |
| Storage | AWS S3 |
| CDN | CloudFront |
| Frontend | React + TypeScript |
| Search | OpenSearch |
| Infra | Kubernetes (later), ECS initially |
| CI/CD | GitHub Actions |
| Observability | Prometheus + Grafana + Loki |

---

# What I would build first if I were you

### Sprint 1

- Tenant resolver
- JWT auth
- RBAC
- User CRUD

### Sprint 2

- Course CRUD
- Lesson upload
- Enrollment APIs

### Sprint 3

- Progress tracking
- Quiz engine
- Certificates PDF

### Sprint 4

- Reporting dashboard
- Notifications
- Tenant branding UI

This sequence gets you a demoable SaaS LMS quickly.

---

# A concise requirement checklist for your PRD

**PRD**
**Use this as the master checklist**
- [ ] Multi-tenant isolation
- [ ] Custom domain & branding
- [ ] RBAC + SSO
- [ ] Organization hierarchy
- [ ] Course & content management
- [ ] SCORM/xAPI
- [ ] Enrollment engine
- [ ] Assessments & grading
- [ ] Certificates & compliance
- [ ] Learner dashboard
- [ ] Instructor workspace
- [ ] Analytics & exports
- [ ] Notifications
- [ ] Public APIs & webhooks
- [ ] Billing & subscriptions
- [ ] Audit logs
- [ ] Encryption & security
- [ ] Backup & disaster recovery
- [ ] Observability & monitoring
- [ ] Feature flags
- [ ] Rate limiting & quotas

---

The next useful step is to convert this into a **full Software Requirements Specification (SRS)** with user stories, database schema, API contracts, and a phased delivery plan suitable for implementation in Spring Boot.
