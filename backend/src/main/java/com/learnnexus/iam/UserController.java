package com.learnnexus.iam;

import com.learnnexus.common.PageResponse;
import com.learnnexus.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Tag(name = "Users", description = "Workspace membership, roles and bulk import.")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "List and search workspace members")
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN','MANAGER','INSTRUCTOR')")
    public PageResponse<UserDtos.Summary> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) User.Status status,
            @RequestParam(required = false) UUID orgUnitId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String sort) {
        return userService.list(query, status, orgUnitId, page, size, sort);
    }

    @Operation(summary = "List the signed-in manager's direct and indirect reports")
    @GetMapping("/my-team")
    @PreAuthorize("hasAnyRole('MANAGER','TENANT_ADMIN','PLATFORM_ADMIN')")
    public PageResponse<UserDtos.Summary> myTeam(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return userService.listForManager(CurrentUser.requireId(), page, size);
    }

    @Operation(summary = "The roles that can be assigned in this workspace")
    @GetMapping("/roles")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public List<UserDtos.RoleOption> roles() {
        return Arrays.stream(RoleCode.values())
                .filter(role -> role != RoleCode.PLATFORM_ADMIN)
                .map(role -> new UserDtos.RoleOption(role, titleOf(role), descriptionOf(role)))
                .toList();
    }

    @Operation(summary = "Full record for one member")
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN','MANAGER','INSTRUCTOR')")
    public UserDtos.Detail get(@PathVariable UUID userId) {
        return userService.get(userId);
    }

    @Operation(summary = "Add a member and optionally email them an invitation")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public UserDtos.Detail create(@Valid @RequestBody UserDtos.CreateRequest request) {
        return userService.create(request);
    }

    @Operation(summary = "Update a member's profile and reporting line")
    @PutMapping("/{userId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public UserDtos.Detail update(@PathVariable UUID userId,
                                  @Valid @RequestBody UserDtos.UpdateRequest request) {
        return userService.update(userId, request);
    }

    @Operation(summary = "Replace a member's roles; ends their existing sessions")
    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public UserDtos.Detail changeRoles(@PathVariable UUID userId,
                                       @Valid @RequestBody UserDtos.RolesRequest request) {
        return userService.changeRoles(userId, request.roles());
    }

    @Operation(summary = "Activate or suspend a member")
    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public UserDtos.Detail changeStatus(@PathVariable UUID userId,
                                        @RequestBody UserDtos.StatusRequest request) {
        return userService.changeStatus(userId, request.status());
    }

    @Operation(summary = "Send a fresh invitation email")
    @PostMapping("/{userId}/resend-invitation")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public ResponseEntity<Void> resendInvitation(@PathVariable UUID userId) {
        userService.resendInvitation(userId);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Remove a member; learning records are retained")
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID userId) {
        userService.deactivate(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Import members from CSV (email, first_name, last_name, job_title, org_unit_code, roles)")
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public UserDtos.ImportResult importCsv(@RequestPart("file") MultipartFile file,
                                           @RequestParam(defaultValue = "true") boolean sendInvitations) {
        return userService.importCsv(file, sendInvitations);
    }

    @Operation(summary = "Update the signed-in user's own profile")
    @PutMapping("/me")
    public UserDtos.Detail updateOwnProfile(@Valid @RequestBody UserDtos.ProfileUpdateRequest request) {
        return userService.updateOwnProfile(request);
    }

    private static String titleOf(RoleCode role) {
        return switch (role) {
            case PLATFORM_ADMIN -> "Platform Super Admin";
            case TENANT_ADMIN -> "Workspace Admin";
            case AUTHOR -> "Content Author";
            case INSTRUCTOR -> "Instructor";
            case MANAGER -> "Manager";
            case LEARNER -> "Learner";
        };
    }

    private static String descriptionOf(RoleCode role) {
        return switch (role) {
            case PLATFORM_ADMIN -> "Operates the platform across every workspace.";
            case TENANT_ADMIN -> "Full control of this workspace: people, courses, branding and billing.";
            case AUTHOR -> "Builds and maintains courses in the content library.";
            case INSTRUCTOR -> "Teaches assigned courses, grades submissions and runs live sessions.";
            case MANAGER -> "Sees progress and compliance for their own part of the organisation.";
            case LEARNER -> "Takes assigned courses and earns certificates.";
        };
    }
}
