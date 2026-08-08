package com.learnnexus.common;

import com.learnnexus.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The sanctioned escape hatch for reads that JPA expresses badly — reporting
 * roll-ups, dashboard aggregates, cross-entity joins.
 *
 * <p>Native SQL bypasses Hibernate's {@code @TenantId} discriminator, which is
 * exactly the isolation guarantee the rest of the system leans on. Every method
 * here therefore binds the current tenant as the <em>first</em> parameter and
 * refuses to run SQL that does not mention {@code tenant_id} at all. That last
 * check is a tripwire, not a proof: it catches the realistic mistake (forgetting
 * the predicate entirely) rather than a determined bypass.
 */
@Component
@RequiredArgsConstructor
public class TenantAwareJdbc {

    private final JdbcTemplate jdbcTemplate;

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        return jdbcTemplate.query(sql, mapper, bind(sql, params));
    }

    /**
     * The first row, if any. Uses {@code ofNullable} because an aggregate such as
     * {@code max(...)} over no rows returns a row containing NULL, and a mapper
     * that maps it to {@code null} is behaving correctly.
     */
    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = query(sql, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.getFirst());
    }

    public List<Map<String, Object>> queryForMaps(String sql, Object... params) {
        return jdbcTemplate.queryForList(sql, bind(sql, params));
    }

    public long queryForLong(String sql, Object... params) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, bind(sql, params));
        return value == null ? 0L : value;
    }

    public int update(String sql, Object... params) {
        return jdbcTemplate.update(sql, bind(sql, params));
    }

    /** For the platform console, which legitimately reads across every tenant. */
    public JdbcTemplate unscoped() {
        return jdbcTemplate;
    }

    private Object[] bind(String sql, Object[] params) {
        if (!sql.contains("tenant_id")) {
            throw new IllegalArgumentException(
                    "Tenant-scoped SQL must constrain tenant_id. Use unscoped() for deliberate cross-tenant reads.");
        }
        UUID tenantId = TenantContext.requireTenantId();
        List<Object> all = new ArrayList<>(params.length + 1);
        all.add(tenantId);
        all.addAll(List.of(params));
        return all.toArray();
    }
}
