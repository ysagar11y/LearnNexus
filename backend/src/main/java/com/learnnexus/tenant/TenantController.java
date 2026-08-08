package com.learnnexus.tenant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Workspace", description = "Settings, branding and feature flags for the current tenant.")
@RestController
@RequestMapping("/api/v1/workspace")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @Operation(summary = "Settings and usage against plan limits")
    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public TenantDtos.Settings settings() {
        return tenantService.settings();
    }

    @Operation(summary = "Update workspace settings")
    @PutMapping("/settings")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public TenantDtos.Settings updateSettings(@Valid @RequestBody TenantDtos.UpdateSettingsRequest request) {
        return tenantService.updateSettings(request);
    }

    @Operation(summary = "Current branding")
    @GetMapping("/branding")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public TenantDtos.Branding branding() {
        return tenantService.branding();
    }

    @Operation(summary = "Update branding; takes effect for everyone on their next page load")
    @PutMapping("/branding")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public TenantDtos.Branding updateBranding(@Valid @RequestBody TenantDtos.UpdateBrandingRequest request) {
        return tenantService.updateBranding(request);
    }

    @Operation(summary = "Feature flags for the current workspace")
    @GetMapping("/features")
    public Map<String, Boolean> features() {
        return tenantService.features();
    }

    @Operation(summary = "Turn a feature on or off")
    @PostMapping("/features")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public Map<String, Boolean> toggleFeature(@Valid @RequestBody TenantDtos.FeatureToggleRequest request) {
        return tenantService.toggleFeature(request.feature(), request.enabled());
    }
}
