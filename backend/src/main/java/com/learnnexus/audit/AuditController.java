package com.learnnexus.audit;

import com.learnnexus.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Audit", description = "The workspace's immutable audit trail.")
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository repository;

    public record Entry(
            Long id,
            String action,
            String entityType,
            String entityId,
            String summary,
            UUID actorId,
            String actorEmail,
            String ipAddress,
            Map<String, Object> metadata,
            Instant createdAt
    ) {}

    @Operation(summary = "Search the audit trail")
    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<Entry> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        var results = repository.findAll(
                AuditLogRepository.matching(
                        action,
                        actorId,
                        from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                        to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()),
                PageRequest.of(page, Math.min(size, 200),
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt")));

        return PageResponse.of(results, entry -> new Entry(
                entry.getId(), entry.getAction(), entry.getEntityType(), entry.getEntityId(),
                entry.getSummary(), entry.getActorId(), entry.getActorEmail(), entry.getIpAddress(),
                entry.getMetadata(), entry.getCreatedAt()));
    }

    @Operation(summary = "The distinct actions present in this workspace's trail, for the filter control")
    @GetMapping("/actions")
    @Transactional(readOnly = true)
    public List<String> actions() {
        return repository.distinctActions();
    }
}
