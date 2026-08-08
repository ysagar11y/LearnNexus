package com.learnnexus.reporting;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Reports", description = "Standard reports with CSV export.")
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN','INSTRUCTOR','MANAGER')")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final DashboardService dashboardService;

    @Operation(summary = "The reports available, with their column definitions")
    @GetMapping
    public List<ReportService.Definition> definitions() {
        return reportService.definitions();
    }

    @Operation(summary = "Admin dashboard roll-up for the current workspace")
    @GetMapping("/dashboard")
    public DashboardService.AdminDashboard dashboard() {
        return dashboardService.adminDashboard();
    }

    @Operation(summary = "Run a report and return its rows")
    @GetMapping("/{key}")
    public ReportService.Result run(
            @PathVariable String key,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID orgUnitId) {
        return reportService.run(key, filters(from, to, courseId, orgUnitId));
    }

    @Operation(summary = "Run a report and download it as CSV")
    @GetMapping(value = "/{key}/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @PathVariable String key,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID orgUnitId) {

        String csv = reportService.runAsCsv(key, filters(from, to, courseId, orgUnitId));
        String filename = key + "-" + LocalDate.now() + ".csv";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                // A BOM keeps Excel from mangling non-ASCII learner names on open.
                .body(prependBom(csv));
    }

    private static byte[] prependBom(String csv) {
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] result = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(body, 0, result, bom.length, body.length);
        return result;
    }

    private static ReportService.Filters filters(LocalDate from, LocalDate to,
                                                 UUID courseId, UUID orgUnitId) {
        Instant fromInstant = from == null ? null : from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        // `to` is inclusive of the whole day the user picked.
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        return new ReportService.Filters(fromInstant, toInstant, courseId, orgUnitId);
    }
}
