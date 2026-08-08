package com.learnnexus.tenancy;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Runs a unit of work in a transaction that begins <em>after</em> the tenant
 * context has been set.
 *
 * <p>This exists because of how Hibernate's discriminator multi-tenancy works:
 * the tenant identifier is read once, when the {@code Session} is opened, and is
 * then fixed for that session's lifetime. Calling
 * {@link TenantContext#runAs} inside an already-open transaction therefore has
 * no effect on the SQL — inserts still carry whatever tenant was current when
 * the surrounding transaction started, which for a background job or a seeder is
 * the no-tenant sentinel.
 *
 * <p>{@link Propagation#REQUIRES_NEW} suspends any outer transaction and forces a
 * fresh session, so the tenant resolved inside {@code runAs} is the one that
 * reaches the database. Anything that switches tenants mid-process — the demo
 * seeder, tenant provisioning, scheduled sweeps — has to go through here.
 */
@Component
public class TenantScopedExecutor {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void run(Runnable action) {
        action.run();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T call(Supplier<T> action) {
        return action.get();
    }

    /** Convenience: set the tenant, then open a transaction bound to it. */
    public void runAs(TenantContext.Snapshot tenant, Runnable action) {
        TenantContext.runAs(tenant, () -> run(action));
    }

    public <T> T callAs(TenantContext.Snapshot tenant, Supplier<T> action) {
        // The array is a mutable holder; runAs takes a Runnable, not a Supplier.
        Object[] result = new Object[1];
        TenantContext.runAs(tenant, () -> result[0] = call(action));
        @SuppressWarnings("unchecked")
        T typed = (T) result[0];
        return typed;
    }
}
