package com.learnnexus.tenant;

import com.learnnexus.tenancy.TenantContext;
import com.learnnexus.tenancy.TenantDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tenant lookup on the hot path of <em>every</em> request.
 *
 * <p>A short in-process TTL cache keeps that lookup off the database without
 * making a suspended or renamed tenant linger for long. Entries are also evicted
 * explicitly whenever a tenant's addressing or status changes, so the TTL is a
 * safety net rather than the primary invalidation mechanism.
 */
@Service
@RequiredArgsConstructor
public class CachingTenantDirectory implements TenantDirectory {

    private static final Duration TTL = Duration.ofSeconds(60);

    private record Entry(TenantContext.Snapshot snapshot, Instant expiresAt) {
        boolean isFresh() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private final TenantRepository tenantRepository;

    private final Map<String, Entry> bySlug = new ConcurrentHashMap<>();
    private final Map<String, Entry> byDomain = new ConcurrentHashMap<>();
    private final Map<UUID, Entry> byId = new ConcurrentHashMap<>();

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantContext.Snapshot> findBySlug(String slug) {
        return lookup(bySlug, slug, () -> tenantRepository.findBySlug(slug));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantContext.Snapshot> findByCustomDomain(String host) {
        return lookup(byDomain, host, () -> tenantRepository.findByCustomDomain(host));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantContext.Snapshot> findById(UUID tenantId) {
        Entry cached = byId.get(tenantId);
        if (cached != null && cached.isFresh()) {
            return Optional.of(cached.snapshot());
        }
        return tenantRepository.findActiveById(tenantId)
                .filter(Tenant::isOperational)
                .map(this::cache);
    }

    @Override
    public void evict(UUID tenantId) {
        byId.remove(tenantId);
        bySlug.values().removeIf(entry -> entry.snapshot().tenantId().equals(tenantId));
        byDomain.values().removeIf(entry -> entry.snapshot().tenantId().equals(tenantId));
    }

    private Optional<TenantContext.Snapshot> lookup(Map<String, Entry> cache, String key,
                                                    java.util.function.Supplier<Optional<Tenant>> loader) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        Entry cached = cache.get(key);
        if (cached != null && cached.isFresh()) {
            return Optional.of(cached.snapshot());
        }
        Optional<Tenant> loaded = loader.get().filter(Tenant::isOperational);
        if (loaded.isEmpty()) {
            cache.remove(key);
            return Optional.empty();
        }
        TenantContext.Snapshot snapshot = cache(loaded.get());
        cache.put(key, new Entry(snapshot, Instant.now().plus(TTL)));
        return Optional.of(snapshot);
    }

    private TenantContext.Snapshot cache(Tenant tenant) {
        TenantContext.Snapshot snapshot = new TenantContext.Snapshot(
                tenant.getId(), tenant.getSlug(), tenant.getName(), tenant.isSystemTenant());
        Instant expiry = Instant.now().plus(TTL);
        byId.put(tenant.getId(), new Entry(snapshot, expiry));
        bySlug.put(tenant.getSlug().toLowerCase(), new Entry(snapshot, expiry));
        if (tenant.getCustomDomain() != null) {
            byDomain.put(tenant.getCustomDomain().toLowerCase(), new Entry(snapshot, expiry));
        }
        return snapshot;
    }
}
