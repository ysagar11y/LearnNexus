package com.learnnexus.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A customer organisation. Deliberately <em>not</em> {@code TenantScoped}: this is
 * the table the discriminator points at, and platform operators read it across
 * every tenant.
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    public enum Status { TRIAL, ACTIVE, SUSPENDED, ARCHIVED }

    public enum Plan { FREE, PRO, ENTERPRISE }

    /** Feature flags a tenant admin or platform operator can toggle. */
    public enum Feature {
        ASSESSMENTS, CERTIFICATES, DISCUSSIONS, LIVE_SESSIONS, GAMIFICATION,
        SELF_ENROLLMENT, PUBLIC_CATALOG, API_ACCESS;

        public String key() {
            return name().toLowerCase();
        }
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(name = "custom_domain")
    private String customDomain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.TRIAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan = Plan.FREE;

    @Column(nullable = false)
    private String timezone = "Asia/Kolkata";

    @Column(nullable = false)
    private String locale = "en-IN";

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "max_users", nullable = false)
    private int maxUsers = 25;

    @Column(name = "max_storage_bytes", nullable = false)
    private long maxStorageBytes = 5L * 1024 * 1024 * 1024;

    @Column(name = "api_rate_limit", nullable = false)
    private int apiRateLimit = 120;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", nullable = false)
    private Map<String, Boolean> features = new HashMap<>();

    @Column(name = "system_tenant", nullable = false)
    private boolean systemTenant = false;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Tenant(UUID id, String slug, String name) {
        this.id = id;
        this.slug = slug;
        this.name = name;
    }

    /** Flags default to on so a new tenant gets the full product unless opted out. */
    public boolean isFeatureEnabled(Feature feature) {
        return features.getOrDefault(feature.key(), Boolean.TRUE);
    }

    public void setFeature(Feature feature, boolean enabled) {
        features.put(feature.key(), enabled);
    }

    public boolean isOperational() {
        return deletedAt == null && (status == Status.ACTIVE || status == Status.TRIAL);
    }
}
