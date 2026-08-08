package com.learnnexus.tenancy;

import java.util.Optional;
import java.util.UUID;

/**
 * Read model used to turn a request's host or tenant header into a
 * {@link TenantContext.Snapshot}. Implemented in the tenant module; declared here
 * so the resolution filter does not depend on tenant persistence internals.
 */
public interface TenantDirectory {

    Optional<TenantContext.Snapshot> findBySlug(String slug);

    Optional<TenantContext.Snapshot> findByCustomDomain(String host);

    Optional<TenantContext.Snapshot> findById(UUID tenantId);

    /** Drops cached entries for a tenant after its slug, domain or status changes. */
    void evict(UUID tenantId);
}
