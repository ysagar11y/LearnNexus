package com.learnnexus.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class TenantDtos {

    private TenantDtos() {
    }

    public record Settings(
            UUID id,
            String slug,
            String name,
            String customDomain,
            Tenant.Status status,
            Tenant.Plan plan,
            String timezone,
            String locale,
            String currency,
            int maxUsers,
            long maxStorageBytes,
            int apiRateLimit,
            Map<String, Boolean> features,
            Instant trialEndsAt,
            Instant createdAt,
            Usage usage
    ) {}

    public record Usage(
            long activeUsers,
            long invitedUsers,
            long courses,
            long publishedCourses,
            long enrollments,
            long certificates,
            long storageBytes,
            int seatUtilisationPercent,
            int storageUtilisationPercent
    ) {}

    public record UpdateSettingsRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 255) String customDomain,
            @NotBlank String timezone,
            @NotBlank String locale,
            @NotBlank @Size(min = 3, max = 3) String currency
    ) {}

    public record Branding(
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
            String emailFromName,
            String emailFooter,
            String customCss
    ) {}

    public record UpdateBrandingRequest(
            String logoUrl,
            String logoDarkUrl,
            String faviconUrl,
            @Min(0) @Max(360) int brandHue,
            @Min(0) @Max(400) int brandChromaMilli,
            @Min(0) @Max(360) int accentHue,
            @Pattern(regexp = "LIGHT|DARK|SYSTEM") String defaultTheme,
            @Size(max = 200) String loginHeadline,
            @Size(max = 400) String loginSubtext,
            @Email @Size(max = 255) String supportEmail,
            @Size(max = 120) String emailFromName,
            @Size(max = 2000) String emailFooter,
            @Size(max = 20000) String customCss
    ) {}

    public record FeatureToggleRequest(
            @NotBlank String feature,
            boolean enabled
    ) {}
}
