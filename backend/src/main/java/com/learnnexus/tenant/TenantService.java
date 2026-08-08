package com.learnnexus.tenant;

import com.learnnexus.audit.AuditService;
import com.learnnexus.common.ApiException;
import com.learnnexus.common.TenantAwareJdbc;
import com.learnnexus.tenancy.TenantContext;
import com.learnnexus.tenancy.TenantDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantBrandingRepository brandingRepository;
    private final TenantDirectory tenantDirectory;
    private final AuditService auditService;
    private final TenantAwareJdbc jdbc;

    @Transactional(readOnly = true)
    public TenantDtos.Settings settings() {
        Tenant tenant = current();
        return toSettings(tenant, usage(tenant));
    }

    @Transactional
    public TenantDtos.Settings updateSettings(TenantDtos.UpdateSettingsRequest request) {
        Tenant tenant = current();

        String domain = normaliseDomain(request.customDomain());
        if (domain != null) {
            tenantRepository.findByCustomDomain(domain)
                    .filter(other -> !other.getId().equals(tenant.getId()))
                    .ifPresent(other -> {
                        throw ApiException.conflict("domain_taken", "That domain is already in use.");
                    });
        }

        tenant.setName(request.name().trim());
        tenant.setCustomDomain(domain);
        tenant.setTimezone(request.timezone());
        tenant.setLocale(request.locale());
        tenant.setCurrency(request.currency().toUpperCase(Locale.ROOT));
        tenant.setUpdatedAt(Instant.now());
        tenantRepository.save(tenant);

        // Name and domain are cached on the request hot path.
        tenantDirectory.evict(tenant.getId());

        auditService.record(AuditService.SETTINGS_UPDATED, "Tenant", tenant.getId(),
                "Workspace settings updated");
        return toSettings(tenant, usage(tenant));
    }

    @Transactional(readOnly = true)
    public TenantDtos.Branding branding() {
        return toBranding(brandingOrDefault());
    }

    @Transactional
    public TenantDtos.Branding updateBranding(TenantDtos.UpdateBrandingRequest request) {
        TenantBranding branding = brandingOrDefault();

        branding.setLogoUrl(blankToNull(request.logoUrl()));
        branding.setLogoDarkUrl(blankToNull(request.logoDarkUrl()));
        branding.setFaviconUrl(blankToNull(request.faviconUrl()));
        branding.setBrandHue(request.brandHue());
        // Chroma arrives as an integer in thousandths so the wire format stays free
        // of floating-point rounding surprises.
        branding.setBrandChroma(BigDecimal.valueOf(request.brandChromaMilli(), 3));
        branding.setAccentHue(request.accentHue());
        branding.setDefaultTheme(request.defaultTheme() == null
                ? TenantBranding.ThemePreference.SYSTEM
                : TenantBranding.ThemePreference.valueOf(request.defaultTheme()));
        branding.setLoginHeadline(blankToNull(request.loginHeadline()));
        branding.setLoginSubtext(blankToNull(request.loginSubtext()));
        branding.setSupportEmail(blankToNull(request.supportEmail()));
        branding.setEmailFromName(blankToNull(request.emailFromName()));
        branding.setEmailFooter(blankToNull(request.emailFooter()));
        branding.setCustomCss(blankToNull(request.customCss()));
        branding.setUpdatedAt(Instant.now());

        brandingRepository.save(branding);
        auditService.record(AuditService.BRANDING_UPDATED, "Tenant", branding.getTenantId(),
                "Branding updated");
        return toBranding(branding);
    }

    @Transactional
    public Map<String, Boolean> toggleFeature(String feature, boolean enabled) {
        Tenant.Feature parsed = Arrays.stream(Tenant.Feature.values())
                .filter(candidate -> candidate.key().equalsIgnoreCase(feature))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("unknown_feature", "No such feature: " + feature));

        Tenant tenant = current();
        tenant.setFeature(parsed, enabled);
        tenant.setUpdatedAt(Instant.now());
        tenantRepository.save(tenant);

        auditService.record(AuditService.SETTINGS_UPDATED, "Tenant", tenant.getId(),
                (enabled ? "Enabled " : "Disabled ") + parsed.key());
        return featureMap(tenant);
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> features() {
        return featureMap(current());
    }

    /** Throws when a feature is switched off, so callers can guard an endpoint in one line. */
    @Transactional(readOnly = true)
    public void requireFeature(Tenant.Feature feature) {
        if (!current().isFeatureEnabled(feature)) {
            throw ApiException.forbidden("The " + feature.key().replace('_', ' ')
                    + " feature is not enabled for this workspace.");
        }
    }

    // -----------------------------------------------------------------

    private Tenant current() {
        return tenantRepository.findActiveById(TenantContext.requireTenantId())
                .orElseThrow(() -> ApiException.notFound("Tenant", TenantContext.requireTenantId()));
    }

    private TenantBranding brandingOrDefault() {
        return brandingRepository.findById(TenantContext.requireTenantId())
                .orElseGet(() -> new TenantBranding(TenantContext.requireTenantId()));
    }

    private TenantDtos.Usage usage(Tenant tenant) {
        String sql = """
                select
                  (select count(*) from users u
                     where u.tenant_id = t.id and u.deleted_at is null and u.status = 'ACTIVE')      as active_users,
                  (select count(*) from users u
                     where u.tenant_id = t.id and u.deleted_at is null and u.status = 'INVITED')     as invited_users,
                  (select count(*) from courses c
                     where c.tenant_id = t.id and c.deleted_at is null)                              as courses,
                  (select count(*) from courses c
                     where c.tenant_id = t.id and c.deleted_at is null and c.status = 'PUBLISHED')   as published_courses,
                  (select count(*) from enrollments e where e.tenant_id = t.id)                      as enrollments,
                  (select count(*) from certificates ct
                     where ct.tenant_id = t.id and ct.revoked_at is null)                            as certificates,
                  (select coalesce(sum(m.size_bytes), 0) from media_assets m where m.tenant_id = t.id) as storage_bytes
                from tenants t
                where t.id = ?
                """;

        return jdbc.unscoped().query(sql, (rs, rowNum) -> {
            long activeUsers = rs.getLong("active_users");
            long storageBytes = rs.getLong("storage_bytes");
            return new TenantDtos.Usage(
                    activeUsers,
                    rs.getLong("invited_users"),
                    rs.getLong("courses"),
                    rs.getLong("published_courses"),
                    rs.getLong("enrollments"),
                    rs.getLong("certificates"),
                    storageBytes,
                    percentage(activeUsers, tenant.getMaxUsers()),
                    percentage(storageBytes, tenant.getMaxStorageBytes()));
        }, tenant.getId()).stream().findFirst()
                .orElse(new TenantDtos.Usage(0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    private static int percentage(long used, long limit) {
        if (limit <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(used)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(limit), 0, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(999))
                .intValue();
    }

    private TenantDtos.Settings toSettings(Tenant tenant, TenantDtos.Usage usage) {
        return new TenantDtos.Settings(
                tenant.getId(), tenant.getSlug(), tenant.getName(), tenant.getCustomDomain(),
                tenant.getStatus(), tenant.getPlan(), tenant.getTimezone(), tenant.getLocale(),
                tenant.getCurrency(), tenant.getMaxUsers(), tenant.getMaxStorageBytes(),
                tenant.getApiRateLimit(), featureMap(tenant), tenant.getTrialEndsAt(),
                tenant.getCreatedAt(), usage);
    }

    private static Map<String, Boolean> featureMap(Tenant tenant) {
        Map<String, Boolean> all = new LinkedHashMap<>();
        for (Tenant.Feature feature : Tenant.Feature.values()) {
            all.put(feature.key(), tenant.isFeatureEnabled(feature));
        }
        return all;
    }

    private TenantDtos.Branding toBranding(TenantBranding branding) {
        return new TenantDtos.Branding(
                branding.getLogoUrl(), branding.getLogoDarkUrl(), branding.getFaviconUrl(),
                branding.getBrandHue(), branding.getBrandChroma().doubleValue(), branding.getAccentHue(),
                branding.getDefaultTheme().name(), branding.getLoginHeadline(), branding.getLoginSubtext(),
                branding.getSupportEmail(), branding.getEmailFromName(), branding.getEmailFooter(),
                branding.getCustomCss());
    }

    private static String normaliseDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        String cleaned = domain.trim().toLowerCase(Locale.ROOT)
                .replaceFirst("^https?://", "")
                .replaceFirst("/.*$", "");
        if (!cleaned.matches("[a-z0-9.-]+\\.[a-z]{2,}")) {
            throw ApiException.badRequest("invalid_domain", "Enter a domain such as learn.acme.com.");
        }
        return cleaned;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
