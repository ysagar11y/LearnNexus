package com.learnnexus.iam;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * All queries here are implicitly restricted to the current tenant by
 * Hibernate's {@code @TenantId} discriminator — see {@code TenantScoped}.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("select u from User u where lower(u.email) = lower(:email) and u.deletedAt is null")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("select u from User u where u.id = :id and u.deletedAt is null")
    Optional<User> findActiveById(@Param("id") UUID id);

    @Query("""
            select u from User u
            where u.deletedAt is null
              and (cast(:query as string) is null
                   or lower(u.firstName) like lower(concat('%', cast(:query as string), '%'))
                   or lower(u.lastName)  like lower(concat('%', cast(:query as string), '%'))
                   or lower(u.email)     like lower(concat('%', cast(:query as string), '%')))
              and (:status is null or u.status = :status)
              and (:orgUnitId is null or u.orgUnitId = :orgUnitId)
            """)
    Page<User> search(@Param("query") String query,
                      @Param("status") User.Status status,
                      @Param("orgUnitId") UUID orgUnitId,
                      Pageable pageable);

    @Query("select u from User u where u.deletedAt is null and u.id in :ids")
    List<User> findAllActiveByIds(@Param("ids") Collection<UUID> ids);

    @Query("select count(u) from User u where u.deletedAt is null")
    long countActive();

    @Query("select count(u) from User u where u.deletedAt is null and u.status = :status")
    long countByStatus(@Param("status") User.Status status);

    @Query("select u from User u where u.deletedAt is null and u.resetTokenHash = :hash")
    Optional<User> findByResetTokenHash(@Param("hash") String hash);

    @Query("select u from User u where u.deletedAt is null and u.inviteTokenHash = :hash")
    Optional<User> findByInviteTokenHash(@Param("hash") String hash);

    /**
     * Users inside an org-unit subtree, addressed by the parent's materialised
     * path. Backs "a manager only sees their own reports".
     */
    @Query("""
            select u from User u
            where u.deletedAt is null
              and u.orgUnitId in (
                  select o.id from OrgUnit o where o.path like concat(:pathPrefix, '%') or o.id = :rootId
              )
            """)
    Page<User> findInSubtree(@Param("pathPrefix") String pathPrefix,
                             @Param("rootId") UUID rootId,
                             Pageable pageable);

    @Query("select u from User u where u.deletedAt is null and array_contains(u.roles, :role) order by u.firstName")
    List<User> findByRole(@Param("role") String role);
}
