-- =====================================================================
-- LearnNexus :: baseline schema
--
-- Multi-tenancy model: shared database, shared schema, discriminator
-- column `tenant_id` on every business table. Isolation is enforced at
-- three layers:
--   1. Hibernate @TenantId  -> every JPQL/criteria query and insert is
--      automatically constrained to the resolved tenant.
--   2. NOT NULL + composite FKs including tenant_id -> a row can never
--      reference a parent belonging to a different tenant.
--   3. Partial unique indexes scoped by tenant_id.
--
-- Platform super-admins live in a reserved "system" tenant so that the
-- authentication path is uniform; cross-tenant reads are only possible
-- through explicitly audited native queries in the `platform` module.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------
-- Tenancy
-- ---------------------------------------------------------------------

CREATE TABLE tenants (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug                VARCHAR(63)  NOT NULL,
    name                VARCHAR(200) NOT NULL,
    custom_domain       VARCHAR(255),
    status              VARCHAR(20)  NOT NULL DEFAULT 'TRIAL',
    plan                VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    timezone            VARCHAR(64)  NOT NULL DEFAULT 'Asia/Kolkata',
    locale              VARCHAR(16)  NOT NULL DEFAULT 'en-IN',
    currency            VARCHAR(3)      NOT NULL DEFAULT 'INR',
    max_users           INTEGER      NOT NULL DEFAULT 25,
    max_storage_bytes   BIGINT       NOT NULL DEFAULT 5368709120,
    api_rate_limit      INTEGER      NOT NULL DEFAULT 120,
    -- Feature flags as a flag->enabled map. A dedicated table buys nothing
    -- here: flags are always read as a complete set for the current tenant.
    features            JSONB        NOT NULL DEFAULT '{}',
    system_tenant       BOOLEAN      NOT NULL DEFAULT FALSE,
    trial_ends_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT tenants_status_chk CHECK (status IN ('TRIAL','ACTIVE','SUSPENDED','ARCHIVED')),
    CONSTRAINT tenants_plan_chk   CHECK (plan   IN ('FREE','PRO','ENTERPRISE'))
);
CREATE UNIQUE INDEX tenants_slug_uq   ON tenants (lower(slug))          WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX tenants_domain_uq ON tenants (lower(custom_domain)) WHERE custom_domain IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE tenant_branding (
    tenant_id        UUID PRIMARY KEY REFERENCES tenants (id) ON DELETE CASCADE,
    logo_url         VARCHAR(500),
    logo_dark_url    VARCHAR(500),
    favicon_url      VARCHAR(500),
    brand_hue        INTEGER      NOT NULL DEFAULT 232,
    brand_chroma     NUMERIC(4,3) NOT NULL DEFAULT 0.130,
    accent_hue       INTEGER      NOT NULL DEFAULT 38,
    default_theme    VARCHAR(10)  NOT NULL DEFAULT 'SYSTEM',
    login_headline   VARCHAR(200),
    login_subtext    VARCHAR(400),
    support_email    VARCHAR(255),
    email_from_name  VARCHAR(120),
    email_footer     TEXT,
    custom_css       TEXT,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT branding_theme_chk CHECK (default_theme IN ('LIGHT','DARK','SYSTEM')),
    CONSTRAINT branding_hue_chk   CHECK (brand_hue BETWEEN 0 AND 360 AND accent_hue BETWEEN 0 AND 360)
);

-- ---------------------------------------------------------------------
-- Organisation hierarchy
-- ---------------------------------------------------------------------

CREATE TABLE org_units (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    parent_id   UUID         REFERENCES org_units (id) ON DELETE CASCADE,
    name        VARCHAR(160) NOT NULL,
    code        VARCHAR(64),
    -- materialised path of ancestor ids, e.g. '/root/child/'; enables
    -- "manager sees own subtree" queries with a single LIKE predicate.
    path        VARCHAR(1024) NOT NULL DEFAULT '/',
    depth       SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id)
);
CREATE INDEX org_units_tenant_idx ON org_units (tenant_id);
CREATE INDEX org_units_path_idx   ON org_units (tenant_id, path varchar_pattern_ops);
CREATE UNIQUE INDEX org_units_code_uq ON org_units (tenant_id, lower(code)) WHERE code IS NOT NULL;

-- ---------------------------------------------------------------------
-- Identity & access
-- ---------------------------------------------------------------------

CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(32)  NOT NULL UNIQUE,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(300),
    scope       VARCHAR(20)  NOT NULL DEFAULT 'TENANT',
    sort_order  SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT roles_scope_chk CHECK (scope IN ('PLATFORM','TENANT'))
);

CREATE TABLE users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    email                 VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(120),
    first_name            VARCHAR(80)  NOT NULL,
    last_name             VARCHAR(80),
    job_title             VARCHAR(120),
    phone                 VARCHAR(32),
    avatar_url            VARCHAR(500),
    status                VARCHAR(20)  NOT NULL DEFAULT 'INVITED',
    org_unit_id           UUID,
    manager_id            UUID,
    locale                VARCHAR(16),
    timezone              VARCHAR(64),
    email_verified_at     TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    mfa_enabled           BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_attempts SMALLINT     NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    invite_token_hash     VARCHAR(120),
    invite_expires_at     TIMESTAMPTZ,
    reset_token_hash      VARCHAR(120),
    reset_expires_at      TIMESTAMPTZ,
    -- Role codes are a closed application enum validated by roles_chk, so a
    -- join table would add a hop without adding integrity.
    roles                 TEXT[]       NOT NULL DEFAULT '{LEARNER}',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ,
    CONSTRAINT users_status_chk CHECK (status IN ('INVITED','ACTIVE','SUSPENDED')),
    CONSTRAINT users_roles_chk  CHECK (roles <@ ARRAY['PLATFORM_ADMIN','TENANT_ADMIN','AUTHOR','INSTRUCTOR','MANAGER','LEARNER']::TEXT[]
                                       AND array_length(roles, 1) >= 1),
    -- composite FKs guarantee a user can never point at another tenant's rows
    CONSTRAINT users_org_unit_fk FOREIGN KEY (tenant_id, org_unit_id) REFERENCES org_units (tenant_id, id) ON DELETE SET NULL,
    UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX users_email_uq    ON users (tenant_id, lower(email)) WHERE deleted_at IS NULL;
CREATE INDEX        users_tenant_idx  ON users (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX        users_manager_idx ON users (tenant_id, manager_id);
CREATE INDEX        users_search_idx  ON users USING gin ((first_name || ' ' || coalesce(last_name,'') || ' ' || email) gin_trgm_ops);
ALTER TABLE users ADD CONSTRAINT users_manager_fk
    FOREIGN KEY (tenant_id, manager_id) REFERENCES users (tenant_id, id) ON DELETE SET NULL;

CREATE INDEX users_roles_idx ON users USING gin (roles);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(120) NOT NULL UNIQUE,
    family_id   UUID         NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    user_agent  VARCHAR(400),
    ip_address  VARCHAR(64),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT refresh_user_fk FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (tenant_id, user_id) WHERE revoked_at IS NULL;

CREATE TABLE api_keys (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name         VARCHAR(120) NOT NULL,
    key_prefix   VARCHAR(16)  NOT NULL,
    key_hash     VARCHAR(120) NOT NULL,
    scopes       VARCHAR(500) NOT NULL DEFAULT 'read',
    last_used_at TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    created_by   UUID,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX api_keys_prefix_uq ON api_keys (key_prefix);

-- ---------------------------------------------------------------------
-- Media
-- ---------------------------------------------------------------------

CREATE TABLE media_assets (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    storage_key  VARCHAR(500) NOT NULL UNIQUE,
    filename     VARCHAR(300) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes   BIGINT       NOT NULL DEFAULT 0,
    kind         VARCHAR(20)  NOT NULL DEFAULT 'OTHER',
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    duration_seconds INTEGER,
    uploaded_by  UUID,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT media_kind_chk   CHECK (kind   IN ('VIDEO','PDF','IMAGE','AUDIO','SCORM','OTHER')),
    CONSTRAINT media_status_chk CHECK (status IN ('PENDING','READY','FAILED')),
    UNIQUE (tenant_id, id)
);
CREATE INDEX media_tenant_idx ON media_assets (tenant_id, created_at DESC);

-- ---------------------------------------------------------------------
-- Catalog
-- ---------------------------------------------------------------------

CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL,
    slug        VARCHAR(140) NOT NULL,
    description VARCHAR(400),
    color       VARCHAR(16),
    sort_order  SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX categories_slug_uq ON categories (tenant_id, lower(slug));

CREATE TABLE courses (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    code              VARCHAR(40),
    title             VARCHAR(240) NOT NULL,
    slug              VARCHAR(260) NOT NULL,
    summary           VARCHAR(600),
    description       TEXT,
    thumbnail_url     VARCHAR(500),
    category_id       UUID,
    owner_id          UUID,
    level             VARCHAR(20)  NOT NULL DEFAULT 'BEGINNER',
    delivery_type     VARCHAR(24)  NOT NULL DEFAULT 'SELF_PACED',
    status            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    language          VARCHAR(16)  NOT NULL DEFAULT 'en',
    version           INTEGER      NOT NULL DEFAULT 1,
    estimated_minutes INTEGER      NOT NULL DEFAULT 0,
    seat_limit        INTEGER,
    enrollment_mode   VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    passing_score     SMALLINT     NOT NULL DEFAULT 70,
    -- Tags and prerequisites are small, read-mostly value collections; keeping
    -- them as arrays avoids two join tables that would each need their own
    -- tenant_id discriminator and composite FK.
    tags              TEXT[]       NOT NULL DEFAULT '{}',
    prerequisite_ids  UUID[]       NOT NULL DEFAULT '{}',
    is_mandatory      BOOLEAN      NOT NULL DEFAULT FALSE,
    certificate_enabled BOOLEAN    NOT NULL DEFAULT TRUE,
    certificate_template_id UUID,
    published_at      TIMESTAMPTZ,
    created_by        UUID,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,
    CONSTRAINT courses_level_chk    CHECK (level         IN ('BEGINNER','INTERMEDIATE','ADVANCED')),
    CONSTRAINT courses_delivery_chk CHECK (delivery_type IN ('SELF_PACED','ILT_VIRTUAL','ILT_CLASSROOM','BLENDED','LEARNING_PATH')),
    CONSTRAINT courses_status_chk   CHECK (status        IN ('DRAFT','IN_REVIEW','PUBLISHED','ARCHIVED')),
    CONSTRAINT courses_enroll_chk   CHECK (enrollment_mode IN ('MANUAL','SELF','INVITE')),
    CONSTRAINT courses_category_fk  FOREIGN KEY (tenant_id, category_id) REFERENCES categories (tenant_id, id) ON DELETE SET NULL,
    CONSTRAINT courses_owner_fk     FOREIGN KEY (tenant_id, owner_id)    REFERENCES users (tenant_id, id) ON DELETE SET NULL,
    UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX courses_slug_uq   ON courses (tenant_id, lower(slug)) WHERE deleted_at IS NULL;
CREATE INDEX courses_tenant_idx      ON courses (tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX courses_search_idx      ON courses USING gin ((title || ' ' || coalesce(summary,'')) gin_trgm_ops);

CREATE INDEX courses_tags_idx ON courses USING gin (tags);

CREATE TABLE course_instructors (
    tenant_id UUID NOT NULL,
    course_id UUID NOT NULL,
    user_id   UUID NOT NULL,
    PRIMARY KEY (course_id, user_id),
    CONSTRAINT course_instructors_course_fk FOREIGN KEY (tenant_id, course_id) REFERENCES courses (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT course_instructors_user_fk   FOREIGN KEY (tenant_id, user_id)   REFERENCES users   (tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX course_instructors_user_idx ON course_instructors (tenant_id, user_id);

CREATE TABLE course_prerequisites (
    tenant_id       UUID NOT NULL,
    course_id       UUID NOT NULL,
    prerequisite_id UUID NOT NULL,
    PRIMARY KEY (course_id, prerequisite_id),
    CONSTRAINT prereq_course_fk FOREIGN KEY (tenant_id, course_id)       REFERENCES courses (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT prereq_target_fk FOREIGN KEY (tenant_id, prerequisite_id) REFERENCES courses (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT prereq_not_self  CHECK (course_id <> prerequisite_id)
);

CREATE TABLE course_modules (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID         NOT NULL,
    course_id  UUID         NOT NULL,
    title      VARCHAR(240) NOT NULL,
    summary    VARCHAR(600),
    sort_order SMALLINT     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT modules_course_fk FOREIGN KEY (tenant_id, course_id) REFERENCES courses (tenant_id, id) ON DELETE CASCADE,
    UNIQUE (tenant_id, id)
);
CREATE INDEX course_modules_course_idx ON course_modules (tenant_id, course_id, sort_order);

CREATE TABLE lessons (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL,
    course_id        UUID         NOT NULL,
    module_id        UUID         NOT NULL,
    title            VARCHAR(240) NOT NULL,
    content_type     VARCHAR(20)  NOT NULL DEFAULT 'HTML',
    content_url      VARCHAR(1000),
    content_html     TEXT,
    asset_id         UUID,
    duration_seconds INTEGER      NOT NULL DEFAULT 0,
    sort_order       SMALLINT     NOT NULL DEFAULT 0,
    is_preview       BOOLEAN      NOT NULL DEFAULT FALSE,
    is_mandatory     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT lessons_type_chk   CHECK (content_type IN ('VIDEO','PDF','HTML','AUDIO','SCORM','LINK','QUIZ')),
    CONSTRAINT lessons_module_fk  FOREIGN KEY (tenant_id, module_id) REFERENCES course_modules (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT lessons_course_fk  FOREIGN KEY (tenant_id, course_id) REFERENCES courses (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT lessons_asset_fk   FOREIGN KEY (tenant_id, asset_id)  REFERENCES media_assets (tenant_id, id) ON DELETE SET NULL,
    UNIQUE (tenant_id, id)
);
CREATE INDEX lessons_module_idx ON lessons (tenant_id, module_id, sort_order);
CREATE INDEX lessons_course_idx ON lessons (tenant_id, course_id);

-- ---------------------------------------------------------------------
-- Enrollment & progress
-- ---------------------------------------------------------------------

CREATE TABLE enrollments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID        NOT NULL,
    course_id        UUID        NOT NULL,
    user_id          UUID        NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    source           VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    assigned_by      UUID,
    progress_percent SMALLINT    NOT NULL DEFAULT 0,
    due_at           TIMESTAMPTZ,
    expires_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    last_accessed_at TIMESTAMPTZ,
    enrolled_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT enrollments_status_chk CHECK (status IN ('ACTIVE','COMPLETED','EXPIRED','WITHDRAWN','WAITLISTED')),
    CONSTRAINT enrollments_source_chk CHECK (source IN ('MANUAL','SELF','INVITE','RULE','IMPORT','API','PATH')),
    CONSTRAINT enrollments_pct_chk    CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT enrollments_course_fk  FOREIGN KEY (tenant_id, course_id) REFERENCES courses (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT enrollments_user_fk    FOREIGN KEY (tenant_id, user_id)   REFERENCES users   (tenant_id, id) ON DELETE CASCADE,
    UNIQUE (tenant_id, course_id, user_id),
    UNIQUE (tenant_id, id)
);
CREATE INDEX enrollments_user_idx    ON enrollments (tenant_id, user_id, status);
CREATE INDEX enrollments_course_idx  ON enrollments (tenant_id, course_id, status);
CREATE INDEX enrollments_due_idx     ON enrollments (tenant_id, due_at) WHERE status = 'ACTIVE' AND due_at IS NOT NULL;

CREATE TABLE lesson_progress (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID        NOT NULL,
    enrollment_id         UUID        NOT NULL,
    lesson_id             UUID        NOT NULL,
    user_id               UUID        NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    seconds_watched       INTEGER     NOT NULL DEFAULT 0,
    last_position_seconds INTEGER     NOT NULL DEFAULT 0,
    completed_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT progress_status_chk CHECK (status IN ('NOT_STARTED','IN_PROGRESS','COMPLETED')),
    CONSTRAINT progress_enroll_fk  FOREIGN KEY (tenant_id, enrollment_id) REFERENCES enrollments (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT progress_lesson_fk  FOREIGN KEY (tenant_id, lesson_id)     REFERENCES lessons     (tenant_id, id) ON DELETE CASCADE,
    UNIQUE (tenant_id, enrollment_id, lesson_id)
);
CREATE INDEX lesson_progress_user_idx ON lesson_progress (tenant_id, user_id);

-- ---------------------------------------------------------------------
-- Assessments
-- ---------------------------------------------------------------------

CREATE TABLE assessments (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL,
    course_id            UUID         NOT NULL,
    lesson_id            UUID,
    title                VARCHAR(240) NOT NULL,
    description          VARCHAR(1000),
    type                 VARCHAR(20)  NOT NULL DEFAULT 'QUIZ',
    time_limit_minutes   INTEGER,
    max_attempts         SMALLINT     NOT NULL DEFAULT 3,
    passing_score        SMALLINT     NOT NULL DEFAULT 70,
    questions_per_attempt SMALLINT,
    shuffle_questions    BOOLEAN      NOT NULL DEFAULT TRUE,
    shuffle_options      BOOLEAN      NOT NULL DEFAULT TRUE,
    negative_marking     NUMERIC(4,2) NOT NULL DEFAULT 0,
    show_correct_answers BOOLEAN      NOT NULL DEFAULT TRUE,
    status               VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT assessments_type_chk   CHECK (type   IN ('QUIZ','EXAM','SURVEY')),
    CONSTRAINT assessments_status_chk CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    CONSTRAINT assessments_course_fk  FOREIGN KEY (tenant_id, course_id) REFERENCES courses (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT assessments_lesson_fk  FOREIGN KEY (tenant_id, lesson_id) REFERENCES lessons (tenant_id, id) ON DELETE SET NULL,
    UNIQUE (tenant_id, id)
);
CREATE INDEX assessments_course_idx ON assessments (tenant_id, course_id);

CREATE TABLE questions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    assessment_id UUID         NOT NULL,
    type          VARCHAR(20)  NOT NULL DEFAULT 'SINGLE_CHOICE',
    prompt        TEXT         NOT NULL,
    explanation   TEXT,
    points        NUMERIC(6,2) NOT NULL DEFAULT 1,
    difficulty    VARCHAR(12)  NOT NULL DEFAULT 'MEDIUM',
    sort_order    SMALLINT     NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT questions_type_chk CHECK (type IN ('SINGLE_CHOICE','MULTI_CHOICE','TRUE_FALSE','SHORT_ANSWER','ESSAY')),
    CONSTRAINT questions_diff_chk CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
    CONSTRAINT questions_assessment_fk FOREIGN KEY (tenant_id, assessment_id) REFERENCES assessments (tenant_id, id) ON DELETE CASCADE,
    UNIQUE (tenant_id, id)
);
CREATE INDEX questions_assessment_idx ON questions (tenant_id, assessment_id, sort_order);

CREATE TABLE question_options (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID     NOT NULL,
    question_id UUID     NOT NULL,
    label       TEXT     NOT NULL,
    is_correct  BOOLEAN  NOT NULL DEFAULT FALSE,
    sort_order  SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT options_question_fk FOREIGN KEY (tenant_id, question_id) REFERENCES questions (tenant_id, id) ON DELETE CASCADE,
    UNIQUE (tenant_id, id)
);
CREATE INDEX question_options_q_idx ON question_options (tenant_id, question_id, sort_order);

CREATE TABLE attempts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL,
    assessment_id      UUID         NOT NULL,
    user_id            UUID         NOT NULL,
    enrollment_id      UUID,
    attempt_number     SMALLINT     NOT NULL DEFAULT 1,
    status             VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
    score              NUMERIC(8,2) NOT NULL DEFAULT 0,
    max_score          NUMERIC(8,2) NOT NULL DEFAULT 0,
    percentage         NUMERIC(5,2) NOT NULL DEFAULT 0,
    passed             BOOLEAN      NOT NULL DEFAULT FALSE,
    requires_grading   BOOLEAN      NOT NULL DEFAULT FALSE,
    time_spent_seconds INTEGER      NOT NULL DEFAULT 0,
    expires_at         TIMESTAMPTZ,
    started_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    submitted_at       TIMESTAMPTZ,
    graded_at          TIMESTAMPTZ,
    graded_by          UUID,
    CONSTRAINT attempts_status_chk CHECK (status IN ('IN_PROGRESS','SUBMITTED','GRADED','EXPIRED')),
    CONSTRAINT attempts_assessment_fk FOREIGN KEY (tenant_id, assessment_id) REFERENCES assessments (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT attempts_user_fk       FOREIGN KEY (tenant_id, user_id)       REFERENCES users       (tenant_id, id) ON DELETE CASCADE,
    UNIQUE (tenant_id, assessment_id, user_id, attempt_number),
    UNIQUE (tenant_id, id)
);
CREATE INDEX attempts_user_idx  ON attempts (tenant_id, user_id, assessment_id);
CREATE INDEX attempts_grade_idx ON attempts (tenant_id, status) WHERE requires_grading = TRUE;

CREATE TABLE attempt_answers (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID    NOT NULL,
    attempt_id     UUID    NOT NULL,
    question_id    UUID    NOT NULL,
    selected_options UUID[] NOT NULL DEFAULT '{}',
    text_answer    TEXT,
    is_correct     BOOLEAN,
    points_awarded NUMERIC(6,2) NOT NULL DEFAULT 0,
    feedback       TEXT,
    answered_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT answers_attempt_fk  FOREIGN KEY (tenant_id, attempt_id)  REFERENCES attempts  (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT answers_question_fk FOREIGN KEY (tenant_id, question_id) REFERENCES questions (tenant_id, id) ON DELETE CASCADE,
    UNIQUE (tenant_id, attempt_id, question_id)
);

-- ---------------------------------------------------------------------
-- Certificates
-- ---------------------------------------------------------------------

CREATE TABLE certificate_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name            VARCHAR(160) NOT NULL,
    html_template   TEXT         NOT NULL,
    orientation     VARCHAR(12)  NOT NULL DEFAULT 'LANDSCAPE',
    validity_months INTEGER,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT cert_tpl_orient_chk CHECK (orientation IN ('LANDSCAPE','PORTRAIT')),
    UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX cert_templates_default_uq ON certificate_templates (tenant_id) WHERE is_default = TRUE;

ALTER TABLE courses ADD CONSTRAINT courses_cert_tpl_fk
    FOREIGN KEY (tenant_id, certificate_template_id) REFERENCES certificate_templates (tenant_id, id) ON DELETE SET NULL;

CREATE TABLE certificates (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL,
    user_id           UUID         NOT NULL,
    course_id         UUID         NOT NULL,
    enrollment_id     UUID,
    template_id       UUID,
    serial_number     VARCHAR(40)  NOT NULL UNIQUE,
    verification_code VARCHAR(64)  NOT NULL UNIQUE,
    recipient_name    VARCHAR(200) NOT NULL,
    course_title      VARCHAR(240) NOT NULL,
    score             NUMERIC(5,2),
    pdf_key           VARCHAR(500),
    issued_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ,
    revoked_at        TIMESTAMPTZ,
    revoked_reason    VARCHAR(400),
    CONSTRAINT certificates_user_fk   FOREIGN KEY (tenant_id, user_id)   REFERENCES users   (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT certificates_course_fk FOREIGN KEY (tenant_id, course_id) REFERENCES courses (tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX certificates_user_idx ON certificates (tenant_id, user_id, issued_at DESC);
CREATE UNIQUE INDEX certificates_enrollment_uq ON certificates (tenant_id, enrollment_id) WHERE enrollment_id IS NOT NULL AND revoked_at IS NULL;

-- ---------------------------------------------------------------------
-- Live sessions (instructor-led)
-- ---------------------------------------------------------------------

CREATE TABLE live_sessions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    course_id     UUID         NOT NULL,
    title         VARCHAR(240) NOT NULL,
    description   VARCHAR(1000),
    provider      VARCHAR(20)  NOT NULL DEFAULT 'OTHER',
    join_url      VARCHAR(1000),
    recording_url VARCHAR(1000),
    instructor_id UUID,
    capacity      INTEGER,
    starts_at     TIMESTAMPTZ  NOT NULL,
    ends_at       TIMESTAMPTZ  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT sessions_provider_chk CHECK (provider IN ('ZOOM','TEAMS','MEET','OTHER')),
    CONSTRAINT sessions_course_fk    FOREIGN KEY (tenant_id, course_id) REFERENCES courses (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT sessions_time_chk     CHECK (ends_at > starts_at),
    UNIQUE (tenant_id, id)
);
CREATE INDEX live_sessions_time_idx ON live_sessions (tenant_id, starts_at);

CREATE TABLE session_attendance (
    tenant_id  UUID        NOT NULL,
    session_id UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
    minutes    INTEGER     NOT NULL DEFAULT 0,
    joined_at  TIMESTAMPTZ,
    PRIMARY KEY (session_id, user_id),
    CONSTRAINT attendance_status_chk  CHECK (status IN ('REGISTERED','ATTENDED','ABSENT','EXCUSED')),
    CONSTRAINT attendance_session_fk  FOREIGN KEY (tenant_id, session_id) REFERENCES live_sessions (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT attendance_user_fk     FOREIGN KEY (tenant_id, user_id)    REFERENCES users (tenant_id, id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- Discussions (course Q&A)
-- ---------------------------------------------------------------------

CREATE TABLE discussion_posts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID        NOT NULL,
    course_id  UUID        NOT NULL,
    lesson_id  UUID,
    parent_id  UUID,
    user_id    UUID        NOT NULL,
    body       TEXT        NOT NULL,
    is_pinned  BOOLEAN     NOT NULL DEFAULT FALSE,
    is_resolved BOOLEAN    NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT posts_course_fk FOREIGN KEY (tenant_id, course_id) REFERENCES courses (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT posts_user_fk   FOREIGN KEY (tenant_id, user_id)   REFERENCES users   (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT posts_parent_fk FOREIGN KEY (parent_id)            REFERENCES discussion_posts (id) ON DELETE CASCADE,
    UNIQUE (tenant_id, id)
);
CREATE INDEX discussion_course_idx ON discussion_posts (tenant_id, course_id, created_at DESC) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------

CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID         NOT NULL,
    user_id    UUID         NOT NULL,
    event      VARCHAR(48)  NOT NULL,
    title      VARCHAR(240) NOT NULL,
    body       VARCHAR(1000),
    link       VARCHAR(500),
    severity   VARCHAR(12)  NOT NULL DEFAULT 'INFO',
    email_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    read_at    TIMESTAMPTZ,
    sent_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT notif_severity_chk CHECK (severity IN ('INFO','SUCCESS','WARNING','CRITICAL')),
    CONSTRAINT notif_email_chk    CHECK (email_status IN ('PENDING','SENT','FAILED','SKIPPED')),
    CONSTRAINT notif_user_fk      FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX notifications_inbox_idx   ON notifications (tenant_id, user_id, created_at DESC);
CREATE INDEX notifications_unread_idx  ON notifications (tenant_id, user_id) WHERE read_at IS NULL;
CREATE INDEX notifications_pending_idx ON notifications (email_status) WHERE email_status = 'PENDING';

-- ---------------------------------------------------------------------
-- Audit
-- ---------------------------------------------------------------------

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    actor_id    UUID,
    actor_email VARCHAR(255),
    action      VARCHAR(80)  NOT NULL,
    entity_type VARCHAR(60),
    entity_id   VARCHAR(64),
    summary     VARCHAR(500),
    ip_address  VARCHAR(64),
    user_agent  VARCHAR(400),
    metadata    JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX audit_tenant_idx ON audit_logs (tenant_id, created_at DESC);
CREATE INDEX audit_actor_idx  ON audit_logs (tenant_id, actor_id, created_at DESC);
CREATE INDEX audit_action_idx ON audit_logs (tenant_id, action, created_at DESC);

-- Audit trail must be append-only. Revoking UPDATE/DELETE at the DB level
-- is applied in production via a dedicated application role; the trigger
-- below makes the intent enforceable regardless of connection role.
CREATE OR REPLACE FUNCTION audit_logs_append_only() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_no_mutate
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_append_only();

-- ---------------------------------------------------------------------
-- Billing
-- ---------------------------------------------------------------------

CREATE TABLE subscriptions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID        NOT NULL UNIQUE REFERENCES tenants (id) ON DELETE CASCADE,
    plan                 VARCHAR(20) NOT NULL DEFAULT 'FREE',
    status               VARCHAR(20) NOT NULL DEFAULT 'TRIALING',
    seats                INTEGER     NOT NULL DEFAULT 25,
    price_per_seat_cents INTEGER     NOT NULL DEFAULT 0,
    currency             VARCHAR(3)     NOT NULL DEFAULT 'INR',
    billing_cycle        VARCHAR(12) NOT NULL DEFAULT 'MONTHLY',
    current_period_start TIMESTAMPTZ NOT NULL DEFAULT now(),
    current_period_end   TIMESTAMPTZ NOT NULL DEFAULT now() + INTERVAL '30 days',
    canceled_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT subs_plan_chk   CHECK (plan   IN ('FREE','PRO','ENTERPRISE')),
    CONSTRAINT subs_status_chk CHECK (status IN ('TRIALING','ACTIVE','PAST_DUE','CANCELED')),
    CONSTRAINT subs_cycle_chk  CHECK (billing_cycle IN ('MONTHLY','ANNUAL'))
);

CREATE TABLE invoices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    subscription_id UUID        REFERENCES subscriptions (id) ON DELETE SET NULL,
    number          VARCHAR(40) NOT NULL UNIQUE,
    amount_cents    BIGINT      NOT NULL DEFAULT 0,
    tax_cents       BIGINT      NOT NULL DEFAULT 0,
    total_cents     BIGINT      NOT NULL DEFAULT 0,
    currency        VARCHAR(3)     NOT NULL DEFAULT 'INR',
    status          VARCHAR(12) NOT NULL DEFAULT 'DRAFT',
    line_items      JSONB       NOT NULL DEFAULT '[]',
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    due_at          TIMESTAMPTZ,
    paid_at         TIMESTAMPTZ,
    CONSTRAINT invoices_status_chk CHECK (status IN ('DRAFT','OPEN','PAID','VOID'))
);
CREATE INDEX invoices_tenant_idx ON invoices (tenant_id, issued_at DESC);

-- ---------------------------------------------------------------------
-- Platform-wide announcements (global, not tenant scoped)
-- ---------------------------------------------------------------------

CREATE TABLE platform_announcements (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title      VARCHAR(240) NOT NULL,
    body       TEXT         NOT NULL,
    severity   VARCHAR(12)  NOT NULL DEFAULT 'INFO',
    starts_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ends_at    TIMESTAMPTZ,
    created_by UUID,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT announce_severity_chk CHECK (severity IN ('INFO','SUCCESS','WARNING','CRITICAL'))
);

-- ---------------------------------------------------------------------
-- Reference data
-- ---------------------------------------------------------------------

INSERT INTO roles (code, name, description, scope, sort_order) VALUES
    ('PLATFORM_ADMIN', 'Platform Super Admin', 'Operates the whole platform across every tenant.',   'PLATFORM', 0),
    ('TENANT_ADMIN',   'Tenant Admin',         'Full administrative control within a single tenant.', 'TENANT',   1),
    ('AUTHOR',         'Content Author',       'Creates and maintains courses in the content library.','TENANT',  2),
    ('INSTRUCTOR',     'Instructor',           'Teaches assigned courses, grades and runs sessions.',  'TENANT',  3),
    ('MANAGER',        'Manager',              'Reports on their own org-unit subtree.',              'TENANT',   4),
    ('LEARNER',        'Learner',              'Consumes enrolled courses.',                          'TENANT',   5);
