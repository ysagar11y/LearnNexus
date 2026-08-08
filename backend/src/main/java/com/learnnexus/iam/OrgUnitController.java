package com.learnnexus.iam;

import com.learnnexus.audit.AuditService;
import com.learnnexus.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Organisation", description = "The tenant's department hierarchy.")
@RestController
@RequestMapping("/api/v1/org-units")
@RequiredArgsConstructor
public class OrgUnitController {

    private final OrgUnitService service;

    public record Node(
            UUID id,
            UUID parentId,
            String name,
            String code,
            int depth,
            long memberCount,
            List<Node> children
    ) {}

    public record SaveRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 64) String code,
            UUID parentId
    ) {}

    @Operation(summary = "The full hierarchy as a tree, with member counts")
    @GetMapping
    public List<Node> tree() {
        return service.tree();
    }

    @Operation(summary = "The hierarchy flattened, ordered for a select control")
    @GetMapping("/flat")
    public List<Node> flat() {
        return service.flat();
    }

    @Operation(summary = "Create a unit")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public Node create(@Valid @RequestBody SaveRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Rename or re-parent a unit")
    @PutMapping("/{unitId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public Node update(@PathVariable UUID unitId, @Valid @RequestBody SaveRequest request) {
        return service.update(unitId, request);
    }

    @Operation(summary = "Delete an empty leaf unit")
    @DeleteMapping("/{unitId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID unitId) {
        service.delete(unitId);
        return ResponseEntity.noContent().build();
    }

    /** Kept beside the controller: the hierarchy has no behaviour worth its own package. */
    @Service
    @RequiredArgsConstructor
    public static class OrgUnitService {

        private static final int MAX_DEPTH = 8;

        private final OrgUnitRepository repository;
        private final UserRepository userRepository;
        private final AuditService auditService;

        @Transactional(readOnly = true)
        public List<Node> tree() {
            List<OrgUnit> units = repository.findAllOrdered();
            Map<UUID, Long> counts = memberCounts();

            Map<UUID, List<OrgUnit>> byParent = new LinkedHashMap<>();
            for (OrgUnit unit : units) {
                byParent.computeIfAbsent(unit.getParentId(), key -> new ArrayList<>()).add(unit);
            }
            return build(byParent, null, counts);
        }

        private List<Node> build(Map<UUID, List<OrgUnit>> byParent, UUID parentId, Map<UUID, Long> counts) {
            return byParent.getOrDefault(parentId, List.of()).stream()
                    .map(unit -> new Node(
                            unit.getId(), unit.getParentId(), unit.getName(), unit.getCode(),
                            unit.getDepth(), counts.getOrDefault(unit.getId(), 0L),
                            build(byParent, unit.getId(), counts)))
                    .toList();
        }

        @Transactional(readOnly = true)
        public List<Node> flat() {
            Map<UUID, Long> counts = memberCounts();
            return repository.findAllOrdered().stream()
                    .map(unit -> new Node(unit.getId(), unit.getParentId(), unit.getName(), unit.getCode(),
                            unit.getDepth(), counts.getOrDefault(unit.getId(), 0L), List.of()))
                    .toList();
        }

        private Map<UUID, Long> memberCounts() {
            Map<UUID, Long> counts = new LinkedHashMap<>();
            userRepository.findAll().stream()
                    .filter(user -> user.getDeletedAt() == null && user.getOrgUnitId() != null)
                    .forEach(user -> counts.merge(user.getOrgUnitId(), 1L, Long::sum));
            return counts;
        }

        @Transactional
        public Node create(SaveRequest request) {
            OrgUnit parent = resolveParent(request.parentId());
            if (parent != null && parent.getDepth() + 1 >= MAX_DEPTH) {
                throw ApiException.badRequest("hierarchy_too_deep",
                        "Organisation hierarchies are limited to " + MAX_DEPTH + " levels.");
            }

            OrgUnit unit = new OrgUnit();
            unit.setName(request.name().trim());
            unit.setCode(request.code() == null || request.code().isBlank() ? null : request.code().trim());
            unit.placeUnder(parent);
            repository.save(unit);

            auditService.record(AuditService.SETTINGS_UPDATED, "OrgUnit", unit.getId(),
                    "Created organisation unit " + unit.getName());
            return toNode(unit, 0L);
        }

        @Transactional
        public Node update(UUID unitId, SaveRequest request) {
            OrgUnit unit = repository.findById(unitId)
                    .orElseThrow(() -> ApiException.notFound("Organisation unit", unitId));

            unit.setName(request.name().trim());
            unit.setCode(request.code() == null || request.code().isBlank() ? null : request.code().trim());

            boolean reparenting = !java.util.Objects.equals(unit.getParentId(), request.parentId());
            if (reparenting) {
                OrgUnit parent = resolveParent(request.parentId());
                if (parent != null) {
                    if (parent.getId().equals(unitId)) {
                        throw ApiException.badRequest("invalid_parent", "A unit cannot be its own parent.");
                    }
                    if (parent.getPath().contains(unitId.toString())) {
                        throw ApiException.badRequest("invalid_parent",
                                "That would place the unit inside its own subtree.");
                    }
                }
                String oldSubtree = unit.subtreePath();
                unit.placeUnder(parent);
                repository.save(unit);
                // Descendants cache their ancestors' ids, so moving a unit has to
                // rewrite every path beneath it.
                rewriteDescendants(unitId, oldSubtree, unit.subtreePath(), unit.getDepth());
            }

            repository.save(unit);
            auditService.record(AuditService.SETTINGS_UPDATED, "OrgUnit", unitId,
                    "Updated organisation unit " + unit.getName());
            return toNode(unit, memberCounts().getOrDefault(unitId, 0L));
        }

        private void rewriteDescendants(UUID rootId, String oldPrefix, String newPrefix, short newDepth) {
            List<OrgUnit> descendants = repository.findSubtree(rootId, oldPrefix).stream()
                    .filter(unit -> !unit.getId().equals(rootId))
                    .toList();
            int depthDelta = newDepth + 1 - (descendants.isEmpty() ? 0 : descendants.getFirst().getDepth());
            for (OrgUnit descendant : descendants) {
                descendant.setPath(newPrefix + descendant.getPath().substring(oldPrefix.length()));
                descendant.setDepth((short) (descendant.getDepth() + depthDelta));
                repository.save(descendant);
            }
        }

        @Transactional
        public void delete(UUID unitId) {
            OrgUnit unit = repository.findById(unitId)
                    .orElseThrow(() -> ApiException.notFound("Organisation unit", unitId));
            if (repository.existsByParentId(unitId)) {
                throw ApiException.conflict("has_children", "Remove or move the child units first.");
            }
            long members = memberCounts().getOrDefault(unitId, 0L);
            if (members > 0) {
                throw ApiException.conflict("has_members",
                        "Move the " + members + " people in this unit somewhere else first.");
            }
            repository.delete(unit);
            auditService.record(AuditService.SETTINGS_UPDATED, "OrgUnit", unitId,
                    "Deleted organisation unit " + unit.getName());
        }

        private OrgUnit resolveParent(UUID parentId) {
            if (parentId == null) {
                return null;
            }
            return repository.findById(parentId)
                    .orElseThrow(() -> ApiException.badRequest("unknown_parent", "That parent unit does not exist."));
        }

        private Node toNode(OrgUnit unit, long memberCount) {
            return new Node(unit.getId(), unit.getParentId(), unit.getName(), unit.getCode(),
                    unit.getDepth(), memberCount, List.of());
        }
    }
}
