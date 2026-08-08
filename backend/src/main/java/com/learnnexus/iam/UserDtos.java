package com.learnnexus.iam;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record Summary(
            UUID id,
            String email,
            String firstName,
            String lastName,
            String displayName,
            String jobTitle,
            String avatarUrl,
            User.Status status,
            Set<RoleCode> roles,
            UUID orgUnitId,
            String orgUnitName,
            Instant lastLoginAt,
            Instant createdAt
    ) {}

    public record Detail(
            UUID id,
            String email,
            String firstName,
            String lastName,
            String displayName,
            String jobTitle,
            String phone,
            String avatarUrl,
            User.Status status,
            Set<RoleCode> roles,
            UUID orgUnitId,
            String orgUnitName,
            UUID managerId,
            String managerName,
            String locale,
            String timezone,
            boolean mfaEnabled,
            Instant lastLoginAt,
            Instant createdAt,
            LearningSnapshot learning,
            /**
             * Set only in the response to creating or resending an invitation, never
             * on a plain fetch. The link is also emailed, but a tenant may not have
             * outbound mail configured — this is what lets an admin hand it to
             * someone directly (Slack, WhatsApp, in person) regardless.
             */
            String inviteUrl
    ) {}

    /** Enough of a learner's record to render an admin's user drawer without extra calls. */
    public record LearningSnapshot(
            long enrolled,
            long completed,
            long overdue,
            long certificates,
            int averageProgress
    ) {}

    public record CreateRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 80) String firstName,
            @Size(max = 80) String lastName,
            @Size(max = 120) String jobTitle,
            @NotEmpty Set<RoleCode> roles,
            UUID orgUnitId,
            UUID managerId,
            boolean sendInvitation
    ) {}

    public record UpdateRequest(
            @Size(max = 80) String firstName,
            @Size(max = 80) String lastName,
            @Size(max = 120) String jobTitle,
            @Size(max = 32) String phone,
            String avatarUrl,
            UUID orgUnitId,
            UUID managerId,
            String locale,
            String timezone
    ) {}

    public record RolesRequest(@NotEmpty Set<RoleCode> roles) {}

    public record StatusRequest(User.Status status) {}

    /** One row of a CSV import, already parsed. */
    public record ImportRow(
            String email,
            String firstName,
            String lastName,
            String jobTitle,
            String orgUnitCode,
            Set<RoleCode> roles
    ) {}

    public record ImportResult(
            int created,
            int updated,
            int skipped,
            List<String> errors
    ) {}

    public record RoleOption(
            RoleCode code,
            String name,
            String description
    ) {}

    public record ProfileUpdateRequest(
            @NotBlank @Size(max = 80) String firstName,
            @Size(max = 80) String lastName,
            @Size(max = 120) String jobTitle,
            @Size(max = 32) String phone,
            String avatarUrl,
            String locale,
            String timezone
    ) {}
}
