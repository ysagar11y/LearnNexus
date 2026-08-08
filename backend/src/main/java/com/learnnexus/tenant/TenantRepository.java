package com.learnnexus.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    @Query("select t from Tenant t where lower(t.slug) = lower(:slug) and t.deletedAt is null")
    Optional<Tenant> findBySlug(@Param("slug") String slug);

    @Query("select t from Tenant t where lower(t.customDomain) = lower(:host) and t.deletedAt is null")
    Optional<Tenant> findByCustomDomain(@Param("host") String host);

    @Query("select t from Tenant t where t.id = :id and t.deletedAt is null")
    Optional<Tenant> findActiveById(@Param("id") UUID id);

    @Query("select t from Tenant t where t.systemTenant = true and t.deletedAt is null")
    Optional<Tenant> findSystemTenant();

    @Query("""
            select t from Tenant t
            where t.deletedAt is null
              and (cast(:query as string) is null
                   or lower(t.name) like lower(concat('%', cast(:query as string), '%'))
                   or lower(t.slug) like lower(concat('%', cast(:query as string), '%')))
              and (:status is null or t.status = :status)
            order by t.createdAt desc
            """)
    List<Tenant> search(@Param("query") String query, @Param("status") Tenant.Status status);

    boolean existsBySlugIgnoreCase(String slug);
}
