package com.learnnexus.platform;

import com.learnnexus.audit.AuditService;
import com.learnnexus.common.ApiException;
import com.learnnexus.common.PageResponse;
import com.learnnexus.common.Slugs;
import com.learnnexus.common.TenantAwareJdbc;
import com.learnnexus.iam.RoleCode;
import com.learnnexus.iam.User;
import com.learnnexus.iam.UserRepository;
import com.learnnexus.security.CurrentUser;
import com.learnnexus.tenancy.TenantContext;
import com.learnnexus.tenancy.TenantDirectory;
import com.learnnexus.tenancy.TenantScopedExecutor;
import com.learnnexus.tenant.Tenant;
import com.learnnexus.tenant.TenantBranding;
import com.learnnexus.tenant.TenantBrandingRepository;
import com.learnnexus.tenant.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The platform super-admin console.
 *
 * <p>This is the one module that legitimately reads across every tenant, so all
 * of its queries go through {@link TenantAwareJdbc#unscoped()} rather than the
 * ORM. Keeping that escape hatch in a single, small, role-gated class is what
 * makes the isolation guarantee elsewhere reviewable.
 *
 * <p>The whole controller sits behind {@code hasRole('PLATFORM_ADMIN')}, applied
 * in {@code SecurityConfig} to {@code /api/v1/platform/**}.
 */
@Tag(name = "Platform", description = "Cross-tenant operations for platform super-admins.")
@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformService platformService;

    public record CreateTenantRequest(
            @NotBlank @Size(max = 200) String name,
            @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,62}",
                    message = "Use lowercase letters, numbers and hyphens") String slug,
            @NotBlank @Email String adminEmail,
            @NotBlank @Size(max = 80) String adminFirstName,
            @Size(max = 80) String adminLastName,
            Tenant.Plan plan,
            Integer maxUsers,
            String timezone,
            String locale,
            String currency
    ) {}

    public record StatusRequest(Tenant.Status status, String reason) {}

    public record QuotaRequest(Integer maxUsers, Long maxStorageBytes, Integer apiRateLimit, Tenant.Plan plan) {}

    public record AnnouncementRequest(
            @NotBlank @Size(max = 240) String title,
            @NotBlank String body,
            String severity,
            Instant startsAt,
            Instant endsAt
    ) {}

    @Operation(summary = "Platform-wide totals across every tenant")
    @GetMapping("/overview")
    public PlatformService.Overview overview() {
        return platformService.overview();
    }

    @Operation(summary = "Search tenants")
    @GetMapping("/tenants")
    public PageResponse<PlatformService.TenantRow> tenants(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Tenant.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return platformService.tenants(query, status, page, size);
    }

    @Operation(summary = "One tenant with its usage and owner")
    @GetMapping("/tenants/{tenantId}")
    public PlatformService.TenantDetail tenant(@PathVariable UUID tenantId) {
        return platformService.tenant(tenantId);
    }

    @Operation(summary = "Provision a tenant and invite its first administrator")
    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformService.TenantDetail createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return platformService.createTenant(request);
    }

    @Operation(summary = "Suspend, reactivate or archive a tenant")
    @PatchMapping("/tenants/{tenantId}/status")
    public PlatformService.TenantDetail changeStatus(@PathVariable UUID tenantId,
                                                     @RequestBody StatusRequest request) {
        return platformService.changeStatus(tenantId, request);
    }

    @Operation(summary = "Adjust a tenant's plan and quotas")
    @PatchMapping("/tenants/{tenantId}/quotas")
    public PlatformService.TenantDetail changeQuotas(@PathVariable UUID tenantId,
                                                     @RequestBody QuotaRequest request) {
        return platformService.changeQuotas(tenantId, request);
    }

    @Operation(summary = "Publish an announcement shown in every workspace")
    @PostMapping("/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> announce(@Valid @RequestBody AnnouncementRequest request) {
        platformService.announce(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Live platform announcements")
    @GetMapping("/announcements")
    public List<PlatformService.Announcement> announcements() {
        return platformService.announcements();
    }

    // -----------------------------------------------------------------

    @Service
    @RequiredArgsConstructor
    public static class PlatformService {

        private final TenantRepository tenantRepository;
        private final TenantBrandingRepository brandingRepository;
        private final UserRepository userRepository;
        private final TenantDirectory tenantDirectory;
        private final TenantScopedExecutor executor;
        private final PasswordEncoder passwordEncoder;
        private final com.learnnexus.security.JwtService jwtService;
        private final com.learnnexus.notification.MailService mailService;
        private final AuditService auditService;
        private final TenantAwareJdbc jdbc;

        public record Overview(
                long tenants,
                long activeTenants,
                long trialTenants,
                long suspendedTenants,
                long users,
                long courses,
                long enrolments,
                long certificates,
                long storageBytes,
                List<PlanBreakdown> plans,
                List<SignupPoint> signups
        ) {}

        public record PlanBreakdown(String plan, long tenants, long users) {}

        public record SignupPoint(Instant month, long tenants) {}

        public record TenantRow(
                UUID id,
                String slug,
                String name,
                String customDomain,
                Tenant.Status status,
                Tenant.Plan plan,
                long users,
                int maxUsers,
                long courses,
                long enrolments,
                Instant trialEndsAt,
                Instant createdAt
        ) {}

        public record TenantDetail(
                TenantRow tenant,
                String ownerName,
                String ownerEmail,
                long storageBytes,
                long maxStorageBytes,
                long certificates,
                Instant lastActivityAt
        ) {}

        public record Announcement(
                UUID id,
                String title,
                String body,
                String severity,
                Instant startsAt,
                Instant endsAt
        ) {}

        // ---------------- Reads ----------------

        @Transactional(readOnly = true)
        public Overview overview() {
            var totals = jdbc.unscoped().queryForMap("""
                    select
                      (select count(*) from tenants where deleted_at is null and system_tenant = false) as tenants,
                      (select count(*) from tenants where deleted_at is null and system_tenant = false
                         and status = 'ACTIVE')                                                          as active_tenants,
                      (select count(*) from tenants where deleted_at is null and system_tenant = false
                         and status = 'TRIAL')                                                           as trial_tenants,
                      (select count(*) from tenants where deleted_at is null and system_tenant = false
                         and status = 'SUSPENDED')                                                       as suspended_tenants,
                      (select count(*) from users where deleted_at is null)                              as users,
                      (select count(*) from courses where deleted_at is null)                            as courses,
                      (select count(*) from enrollments)                                                 as enrolments,
                      (select count(*) from certificates where revoked_at is null)                       as certificates,
                      (select coalesce(sum(size_bytes), 0) from media_assets)                            as storage_bytes
                    """);

            List<PlanBreakdown> plans = jdbc.unscoped().query("""
                    select t.plan,
                           count(distinct t.id) as tenants,
                           count(u.id)          as users
                    from tenants t
                    left join users u on u.tenant_id = t.id and u.deleted_at is null
                    where t.deleted_at is null and t.system_tenant = false
                    group by t.plan
                    order by t.plan
                    """, (rs, rowNum) -> new PlanBreakdown(
                    rs.getString("plan"), rs.getLong("tenants"), rs.getLong("users")));

            List<SignupPoint> signups = jdbc.unscoped().query("""
                    with months as (
                        select generate_series(
                            date_trunc('month', now() - interval '11 months'),
                            date_trunc('month', now()),
                            interval '1 month') as month
                    )
                    select m.month, count(t.id) as tenants
                    from months m
                    left join tenants t on date_trunc('month', t.created_at) = m.month
                         and t.deleted_at is null and t.system_tenant = false
                    group by m.month
                    order by m.month
                    """, (rs, rowNum) -> new SignupPoint(
                    rs.getTimestamp("month").toInstant(), rs.getLong("tenants")));

            return new Overview(
                    number(totals.get("tenants")), number(totals.get("active_tenants")),
                    number(totals.get("trial_tenants")), number(totals.get("suspended_tenants")),
                    number(totals.get("users")), number(totals.get("courses")),
                    number(totals.get("enrolments")), number(totals.get("certificates")),
                    number(totals.get("storage_bytes")), plans, signups);
        }

        @Transactional(readOnly = true)
        public PageResponse<TenantRow> tenants(String query, Tenant.Status status, int page, int size) {
            int limit = Math.min(size, 100);
            int offset = page * limit;
            String like = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
            String statusName = status == null ? null : status.name();

            List<TenantRow> rows = jdbc.unscoped().query("""
                    select t.id, t.slug, t.name, t.custom_domain, t.status, t.plan, t.max_users,
                           t.trial_ends_at, t.created_at,
                           (select count(*) from users u
                             where u.tenant_id = t.id and u.deleted_at is null)          as users,
                           (select count(*) from courses c
                             where c.tenant_id = t.id and c.deleted_at is null)          as courses,
                           (select count(*) from enrollments e where e.tenant_id = t.id) as enrolments
                    from tenants t
                    where t.deleted_at is null and t.system_tenant = false
                      and (cast(? as text) is null
                           or lower(t.name) like cast(? as text)
                           or lower(t.slug) like cast(? as text))
                      and (cast(? as text) is null or t.status = cast(? as text))
                    order by t.created_at desc
                    limit ? offset ?
                    """, (rs, rowNum) -> mapRow(rs), like, like, like, statusName, statusName, limit, offset);

            long total = jdbc.unscoped().queryForObject("""
                    select count(*) from tenants t
                    where t.deleted_at is null and t.system_tenant = false
                      and (cast(? as text) is null
                           or lower(t.name) like cast(? as text)
                           or lower(t.slug) like cast(? as text))
                      and (cast(? as text) is null or t.status = cast(? as text))
                    """, Long.class, like, like, like, statusName, statusName);

            int totalPages = (int) Math.ceil(total / (double) limit);
            return new PageResponse<>(rows, page, limit, total, totalPages, page + 1 < totalPages);
        }

        @Transactional(readOnly = true)
        public TenantDetail tenant(UUID tenantId) {
            TenantRow row = jdbc.unscoped().query("""
                    select t.id, t.slug, t.name, t.custom_domain, t.status, t.plan, t.max_users,
                           t.trial_ends_at, t.created_at,
                           (select count(*) from users u
                             where u.tenant_id = t.id and u.deleted_at is null)          as users,
                           (select count(*) from courses c
                             where c.tenant_id = t.id and c.deleted_at is null)          as courses,
                           (select count(*) from enrollments e where e.tenant_id = t.id) as enrolments
                    from tenants t
                    where t.id = ? and t.deleted_at is null
                    """, (rs, rowNum) -> mapRow(rs), tenantId).stream().findFirst()
                    .orElseThrow(() -> ApiException.notFound("Tenant", tenantId));

            var extra = jdbc.unscoped().queryForMap("""
                    select t.max_storage_bytes,
                           (select coalesce(sum(m.size_bytes), 0) from media_assets m
                             where m.tenant_id = t.id)                                      as storage_bytes,
                           (select count(*) from certificates c
                             where c.tenant_id = t.id and c.revoked_at is null)              as certificates,
                           (select max(u.last_login_at) from users u where u.tenant_id = t.id) as last_activity,
                           (select u.first_name || ' ' || coalesce(u.last_name, '') from users u
                             where u.tenant_id = t.id and u.deleted_at is null
                               and u.roles @> array['TENANT_ADMIN']::text[]
                             order by u.created_at limit 1)                                  as owner_name,
                           (select u.email from users u
                             where u.tenant_id = t.id and u.deleted_at is null
                               and u.roles @> array['TENANT_ADMIN']::text[]
                             order by u.created_at limit 1)                                  as owner_email
                    from tenants t where t.id = ?
                    """, tenantId);

            java.sql.Timestamp lastActivity = (java.sql.Timestamp) extra.get("last_activity");
            return new TenantDetail(
                    row,
                    (String) extra.get("owner_name"),
                    (String) extra.get("owner_email"),
                    number(extra.get("storage_bytes")),
                    number(extra.get("max_storage_bytes")),
                    number(extra.get("certificates")),
                    lastActivity == null ? null : lastActivity.toInstant());
        }

        // ---------------- Writes ----------------

        @Transactional
        public TenantDetail createTenant(CreateTenantRequest request) {
            String slug = request.slug() == null || request.slug().isBlank()
                    ? Slugs.unique(request.name(), tenantRepository::existsBySlugIgnoreCase)
                    : request.slug().trim().toLowerCase();

            if (tenantRepository.existsBySlugIgnoreCase(slug)) {
                throw ApiException.conflict("slug_taken", "That workspace address is already in use.");
            }

            Tenant tenant = new Tenant(UUID.randomUUID(), slug, request.name().trim());
            tenant.setPlan(request.plan() == null ? Tenant.Plan.FREE : request.plan());
            tenant.setStatus(Tenant.Status.TRIAL);
            tenant.setTrialEndsAt(Instant.now().plus(30, ChronoUnit.DAYS));
            if (request.maxUsers() != null) {
                tenant.setMaxUsers(request.maxUsers());
            }
            if (request.timezone() != null) tenant.setTimezone(request.timezone());
            if (request.locale() != null) tenant.setLocale(request.locale());
            if (request.currency() != null) tenant.setCurrency(request.currency().toUpperCase());
            tenantRepository.save(tenant);

            brandingRepository.save(new TenantBranding(tenant.getId()));

            // The first admin is created inside the new tenant's context so the
            // @TenantId discriminator stamps the right value on the row.
            var snapshot = new TenantContext.Snapshot(
                    tenant.getId(), tenant.getSlug(), tenant.getName(), false);

            String inviteToken = jwtService.generateRefreshToken();
            // A new transaction, opened once the tenant context is set, so the
            // @TenantId discriminator stamps the new tenant rather than this one.
            executor.runAs(snapshot, () -> {
                User admin = new User();
                admin.setEmail(request.adminEmail().trim().toLowerCase());
                admin.setFirstName(request.adminFirstName().trim());
                admin.setLastName(request.adminLastName());
                admin.setStatus(User.Status.INVITED);
                admin.setRoleSet(Set.of(RoleCode.TENANT_ADMIN));
                admin.setInviteTokenHash(jwtService.hashToken(inviteToken));
                admin.setInviteExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
                userRepository.save(admin);

                mailService.sendInvitation(admin, snapshot, inviteToken,
                        CurrentUser.find().map(p -> p.displayName()).orElse("The LearnNexus team"));
            });

            auditService.record(AuditService.TENANT_CREATED, "Tenant", tenant.getId(),
                    "Provisioned workspace " + tenant.getName(),
                    java.util.Map.of("slug", slug, "plan", tenant.getPlan().name()));

            return tenant(tenant.getId());
        }

        @Transactional
        public TenantDetail changeStatus(UUID tenantId, StatusRequest request) {
            Tenant tenant = requireTenant(tenantId);
            if (tenant.isSystemTenant()) {
                throw ApiException.forbidden("The platform workspace cannot be suspended.");
            }

            tenant.setStatus(request.status());
            tenant.setUpdatedAt(Instant.now());
            tenantRepository.save(tenant);
            // Suspension has to take effect on the next request, not in a minute.
            tenantDirectory.evict(tenantId);

            auditService.record(AuditService.TENANT_STATUS_CHANGED, "Tenant", tenantId,
                    tenant.getName() + " set to " + request.status(),
                    java.util.Map.of("reason", request.reason() == null ? "" : request.reason()));

            return tenant(tenantId);
        }

        @Transactional
        public TenantDetail changeQuotas(UUID tenantId, QuotaRequest request) {
            Tenant tenant = requireTenant(tenantId);

            if (request.plan() != null) tenant.setPlan(request.plan());
            if (request.maxUsers() != null) tenant.setMaxUsers(request.maxUsers());
            if (request.maxStorageBytes() != null) tenant.setMaxStorageBytes(request.maxStorageBytes());
            if (request.apiRateLimit() != null) tenant.setApiRateLimit(request.apiRateLimit());
            tenant.setUpdatedAt(Instant.now());
            tenantRepository.save(tenant);

            auditService.record(AuditService.TENANT_STATUS_CHANGED, "Tenant", tenantId,
                    "Updated plan and quotas for " + tenant.getName());
            return tenant(tenantId);
        }

        @Transactional
        public void announce(AnnouncementRequest request) {
            jdbc.unscoped().update("""
                    insert into platform_announcements (title, body, severity, starts_at, ends_at, created_by)
                    values (?, ?, ?, coalesce(cast(? as timestamptz), now()), cast(? as timestamptz), ?)
                    """,
                    request.title(), request.body(),
                    request.severity() == null ? "INFO" : request.severity(),
                    request.startsAt() == null ? null : java.sql.Timestamp.from(request.startsAt()),
                    request.endsAt() == null ? null : java.sql.Timestamp.from(request.endsAt()),
                    CurrentUser.requireId());
        }

        @Transactional(readOnly = true)
        public List<Announcement> announcements() {
            return jdbc.unscoped().query("""
                    select id, title, body, severity, starts_at, ends_at
                    from platform_announcements
                    where starts_at <= now() and (ends_at is null or ends_at > now())
                    order by starts_at desc
                    """, (rs, rowNum) -> new Announcement(
                    rs.getObject("id", UUID.class),
                    rs.getString("title"),
                    rs.getString("body"),
                    rs.getString("severity"),
                    rs.getTimestamp("starts_at").toInstant(),
                    rs.getTimestamp("ends_at") == null ? null : rs.getTimestamp("ends_at").toInstant()));
        }

        private Tenant requireTenant(UUID tenantId) {
            return tenantRepository.findActiveById(tenantId)
                    .orElseThrow(() -> ApiException.notFound("Tenant", tenantId));
        }

        private static TenantRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
            return new TenantRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("slug"),
                    rs.getString("name"),
                    rs.getString("custom_domain"),
                    Tenant.Status.valueOf(rs.getString("status")),
                    Tenant.Plan.valueOf(rs.getString("plan")),
                    rs.getLong("users"),
                    rs.getInt("max_users"),
                    rs.getLong("courses"),
                    rs.getLong("enrolments"),
                    rs.getTimestamp("trial_ends_at") == null ? null : rs.getTimestamp("trial_ends_at").toInstant(),
                    rs.getTimestamp("created_at").toInstant());
        }

        private static long number(Object value) {
            return value instanceof Number n ? n.longValue() : 0L;
        }
    }
}
