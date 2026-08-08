package com.learnnexus.tenancy;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

/**
 * Base class for every entity that belongs to exactly one tenant.
 *
 * <p>{@link TenantId} makes Hibernate append a {@code tenant_id = ?} restriction
 * to every generated select, update and delete for the entity, and populate the
 * column on insert from {@link CurrentTenantResolver}. Isolation therefore does
 * not depend on each repository method remembering to filter — a query has to be
 * written in native SQL to escape it, which is deliberate and grep-able.
 */
@MappedSuperclass
@Getter
public abstract class TenantScoped {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
}
