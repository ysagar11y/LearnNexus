package com.learnnexus.audit;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Audit search is built with {@link Specification} rather than a single JPQL
 * statement with four optional filters.
 *
 * <p>The reason is concrete: the usual
 * {@code (:param is null or column = :param)} idiom makes PostgreSQL evaluate
 * {@code $n IS NULL} on a bind whose type it cannot infer, which it either
 * rejects outright or types as {@code bytea} — producing
 * "cannot cast type bytea to uuid". Adding a predicate only when the filter is
 * actually set sidesteps the problem instead of papering over it with casts.
 *
 * <p>Criteria queries still go through Hibernate's {@code @TenantId}
 * discriminator, so tenant isolation is unaffected.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    @Query("select distinct a.action from AuditLog a order by a.action")
    List<String> distinctActions();

    static Specification<AuditLog> matching(String action, UUID actorId, Instant from, Instant to) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>(4);

            if (action != null && !action.isBlank()) {
                predicates.add(builder.equal(root.get("action"), action));
            }
            if (actorId != null) {
                predicates.add(builder.equal(root.get("actorId"), actorId));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
