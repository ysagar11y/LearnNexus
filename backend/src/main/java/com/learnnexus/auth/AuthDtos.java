package com.learnnexus.auth;

import com.learnnexus.iam.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Request and response payloads for the authentication endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {}

    public record ForgotPasswordRequest(
            @NotBlank @Email String email
    ) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 10, max = 200) String password
    ) {}

    public record AcceptInviteRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 10, max = 200) String password,
            @NotBlank @Size(max = 80) String firstName,
            @Size(max = 80) String lastName
    ) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 10, max = 200) String newPassword
    ) {}

    public record SessionResponse(
            String accessToken,
            String refreshToken,
            Instant accessTokenExpiresAt,
            ProfileResponse user
    ) {}

    public record ProfileResponse(
            UUID id,
            UUID tenantId,
            String tenantSlug,
            String tenantName,
            String email,
            String firstName,
            String lastName,
            String displayName,
            String jobTitle,
            String avatarUrl,
            Set<RoleCode> roles,
            RoleCode primaryRole,
            UUID orgUnitId,
            String orgUnitName,
            String locale,
            String timezone,
            Instant lastLoginAt
    ) {}

    /**
     * Everything the sign-in screen needs before a user exists: which workspace
     * they are signing into and how it should look.
     */
    public record TenantPublicResponse(
            String slug,
            String name,
            String logoUrl,
            String logoDarkUrl,
            String faviconUrl,
            int brandHue,
            double brandChroma,
            int accentHue,
            String defaultTheme,
            String loginHeadline,
            String loginSubtext,
            String supportEmail,
            boolean selfEnrollmentEnabled,
            boolean publicCatalogEnabled
    ) {}
}
