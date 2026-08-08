package com.learnnexus.tenancy;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Bridges {@link TenantContext} into Hibernate's discriminator multi-tenancy.
 *
 * <p>Registering itself as a {@link HibernatePropertiesCustomizer} avoids relying
 * on auto-detection ordering during context startup.
 */
@Component
public class CurrentTenantResolver
        implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        // Never null: an unresolved context yields a sentinel that matches no row,
        // so a missing tenant produces empty results rather than a cross-tenant read.
        return TenantContext.tenantIdOrSentinel();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // Sessions are request-scoped and never outlive their tenant context.
        return false;
    }

    @Override
    public boolean isRoot(UUID tenantId) {
        // No tenant is privileged at the ORM layer. Cross-tenant reads must go
        // through the explicitly audited native queries in the platform module.
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
