package com.learnnexus.tenant;

import com.learnnexus.auth.AuthDtos;
import com.learnnexus.common.ApiException;
import com.learnnexus.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated tenant metadata. The sign-in screen calls this first so it can
 * paint the customer's own branding before anyone has a session.
 */
@Tag(name = "Public", description = "Endpoints reachable without a session.")
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicTenantController {

    private final TenantRepository tenantRepository;
    private final TenantBrandingRepository brandingRepository;

    @Operation(summary = "Branding and sign-in copy for the resolved tenant")
    @GetMapping("/tenant")
    public AuthDtos.TenantPublicResponse currentTenant() {
        TenantContext.Snapshot snapshot = TenantContext.current()
                .orElseThrow(() -> ApiException.notFound("No workspace matches this address."));

        Tenant tenant = tenantRepository.findActiveById(snapshot.tenantId())
                .orElseThrow(() -> ApiException.notFound("No workspace matches this address."));

        TenantBranding branding = brandingRepository.findById(tenant.getId())
                .orElseGet(() -> new TenantBranding(tenant.getId()));

        return new AuthDtos.TenantPublicResponse(
                tenant.getSlug(),
                tenant.getName(),
                branding.getLogoUrl(),
                branding.getLogoDarkUrl(),
                branding.getFaviconUrl(),
                branding.getBrandHue(),
                branding.getBrandChroma().doubleValue(),
                branding.getAccentHue(),
                branding.getDefaultTheme().name(),
                branding.getLoginHeadline(),
                branding.getLoginSubtext(),
                branding.getSupportEmail(),
                tenant.isFeatureEnabled(Tenant.Feature.SELF_ENROLLMENT),
                tenant.isFeatureEnabled(Tenant.Feature.PUBLIC_CATALOG)
        );
    }
}
