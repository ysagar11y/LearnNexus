package com.learnnexus.tenancy;

import com.learnnexus.common.ApiException;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Holds the tenant that the current thread is acting on behalf of.
 *
 * <p>Populated once per request by {@link TenantResolutionFilter} and read by
 * {@link CurrentTenantResolver}, which feeds Hibernate's discriminator-based
 * multi-tenancy. Because every tenant-scoped query derives its restriction from
 * this value, an unset context must never be interpreted as "all tenants": the
 * resolver falls back to {@link #NO_TENANT}, a sentinel that matches no row.
 */
public final class TenantContext {

    /**
     * Sentinel returned when no tenant has been resolved. Queries restricted to
     * it return nothing, which makes "forgot to resolve the tenant" fail closed.
     */
    public static final UUID NO_TENANT = new UUID(0L, 0L);

    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public record Snapshot(UUID tenantId, String slug, String name, boolean systemTenant) {
    }

    public static void set(Snapshot snapshot) {
        CURRENT.set(snapshot);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Optional<Snapshot> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID tenantIdOrSentinel() {
        Snapshot snapshot = CURRENT.get();
        return snapshot == null ? NO_TENANT : snapshot.tenantId();
    }

    public static UUID requireTenantId() {
        Snapshot snapshot = CURRENT.get();
        if (snapshot == null) {
            throw ApiException.badRequest("tenant_unresolved", "No tenant is associated with this request.");
        }
        return snapshot.tenantId();
    }

    public static Snapshot require() {
        Snapshot snapshot = CURRENT.get();
        if (snapshot == null) {
            throw ApiException.badRequest("tenant_unresolved", "No tenant is associated with this request.");
        }
        return snapshot;
    }

    public static boolean isSystemTenant() {
        Snapshot snapshot = CURRENT.get();
        return snapshot != null && snapshot.systemTenant();
    }

    /**
     * Runs {@code action} as though the request had resolved to {@code snapshot},
     * restoring the previous context afterwards. Used by schedulers and seeders,
     * which have no HTTP request to derive a tenant from.
     */
    public static <T> T callAs(Snapshot snapshot, Callable<T> action) throws Exception {
        Snapshot previous = CURRENT.get();
        CURRENT.set(snapshot);
        try {
            return action.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(Snapshot snapshot, Runnable action) {
        Snapshot previous = CURRENT.get();
        CURRENT.set(snapshot);
        try {
            action.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
