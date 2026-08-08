package com.learnnexus.reporting;

import com.learnnexus.common.ApiException;
import com.learnnexus.common.TenantAwareJdbc;
import com.learnnexus.iam.OrgUnit;
import com.learnnexus.iam.OrgUnitRepository;
import com.learnnexus.iam.RoleCode;
import com.learnnexus.iam.UserRepository;
import com.learnnexus.security.AppUserPrincipal;
import com.learnnexus.security.CurrentUser;
import com.learnnexus.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the standard reports and renders them as JSON or CSV.
 *
 * <p>Managers are transparently restricted to their own org-unit subtree, so the
 * same report key means "the whole workspace" to an admin and "my team" to a
 * manager without the client having to know the difference.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final TenantAwareJdbc jdbc;
    private final OrgUnitRepository orgUnitRepository;
    private final UserRepository userRepository;

    public record Definition(String key, String title, String description,
                             List<ReportCatalog.Column> columns) {}

    public record Filters(Instant from, Instant to, UUID courseId, UUID orgUnitId) {}

    public record Result(
            String key,
            String title,
            List<ReportCatalog.Column> columns,
            List<Map<String, Object>> rows,
            int rowCount,
            Instant generatedAt
    ) {}

    public List<Definition> definitions() {
        return Arrays.stream(ReportCatalog.values())
                .map(report -> new Definition(report.key(), report.title(),
                        report.description(), report.columns()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Result run(String key, Filters filters) {
        ReportCatalog report = resolve(key);
        String orgPath = effectiveOrgPath(filters.orgUnitId());

        Object[] params = bind(report, filters, orgPath);
        List<Map<String, Object>> rows = jdbc.unscoped().queryForList(report.sql(), params);

        // Normalise types the JSON layer would otherwise render inconsistently.
        List<Map<String, Object>> cleaned = rows.stream().map(row -> {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (ReportCatalog.Column column : report.columns()) {
                Object value = row.get(column.key());
                copy.put(column.key(), value instanceof Timestamp timestamp
                        ? timestamp.toInstant() : value);
            }
            return copy;
        }).toList();

        return new Result(report.key(), report.title(), report.columns(),
                cleaned, cleaned.size(), Instant.now());
    }

    @Transactional(readOnly = true)
    public String runAsCsv(String key, Filters filters) {
        Result result = run(key, filters);

        StringWriter writer = new StringWriter();
        String[] headers = result.columns().stream()
                .map(ReportCatalog.Column::label).toArray(String[]::new);

        try (CSVPrinter printer = new CSVPrinter(writer,
                CSVFormat.DEFAULT.builder().setHeader(headers).build())) {
            for (Map<String, Object> row : result.rows()) {
                List<Object> values = new ArrayList<>(result.columns().size());
                for (ReportCatalog.Column column : result.columns()) {
                    Object value = row.get(column.key());
                    values.add(value == null ? "" : value);
                }
                printer.printRecord(values);
            }
        } catch (IOException ex) {
            throw ApiException.unprocessable("export_failed", "The report could not be exported.");
        }
        return writer.toString();
    }

    private ReportCatalog resolve(String key) {
        try {
            return ReportCatalog.byKey(key);
        } catch (IllegalArgumentException ex) {
            throw ApiException.notFound("No report named '" + key + "'.");
        }
    }

    /**
     * Builds the bind array from the report's declared slot order, and refuses to
     * run a statement that does not bind the tenant exactly once — the one thing
     * that must never be wrong in a report that reads across tables natively.
     */
    private Object[] bind(ReportCatalog report, Filters filters, String orgPath) {
        List<ReportCatalog.Slot> slots = report.slots();
        long tenantBinds = slots.stream().filter(slot -> slot == ReportCatalog.Slot.TENANT).count();
        if (tenantBinds != 1) {
            throw new IllegalStateException(
                    "Report " + report.key() + " must bind the tenant exactly once, found " + tenantBinds);
        }

        UUID tenantId = TenantContext.requireTenantId();
        Object[] params = new Object[slots.size()];
        for (int index = 0; index < slots.size(); index++) {
            params[index] = switch (slots.get(index)) {
                case TENANT -> tenantId;
                case FROM -> filters.from() == null ? null : Timestamp.from(filters.from());
                case TO -> filters.to() == null ? null : Timestamp.from(filters.to());
                case COURSE -> filters.courseId();
                case ORG_PATH -> orgPath;
            };
        }
        return params;
    }

    /**
     * Resolves the org-unit path a report should be limited to.
     *
     * <p>An explicit filter is honoured for privileged roles. A manager is always
     * confined to their own subtree, whatever they asked for — this is the check
     * that makes "Manager: team reporting" a real boundary rather than a label.
     */
    private String effectiveOrgPath(UUID requestedOrgUnitId) {
        AppUserPrincipal principal = CurrentUser.require();
        boolean privileged = principal.hasAnyRole(
                RoleCode.TENANT_ADMIN, RoleCode.PLATFORM_ADMIN, RoleCode.INSTRUCTOR);

        if (privileged) {
            return requestedOrgUnitId == null ? null : subtreePathOf(requestedOrgUnitId);
        }

        if (!principal.hasRole(RoleCode.MANAGER)) {
            throw ApiException.forbidden("You do not have access to reports.");
        }

        UUID ownUnit = userRepository.findActiveById(principal.userId())
                .map(user -> user.getOrgUnitId())
                .orElse(null);
        if (ownUnit == null) {
            // A manager with no unit manages nobody; a path that matches nothing
            // is the correct answer rather than an error.
            return "/__no_org_unit__/";
        }
        return subtreePathOf(ownUnit);
    }

    private String subtreePathOf(UUID orgUnitId) {
        OrgUnit unit = orgUnitRepository.findById(orgUnitId)
                .orElseThrow(() -> ApiException.notFound("Organisation unit", orgUnitId));
        return unit.subtreePath();
    }
}
