package com.learnnexus.tenant;

import com.learnnexus.config.DesignSystem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-tenant visual identity.
 *
 * <p>Colour is stored as an OKLCH hue plus chroma rather than a hex triplet. The
 * SPA derives an entire accessible ramp (surface tints through to hover states)
 * from those two numbers, so a tenant admin picking one colour cannot produce a
 * palette with unreadable contrast — which is exactly what happens when each
 * shade is configured independently.
 */
@Entity
@Table(name = "tenant_branding")
@Getter
@Setter
@NoArgsConstructor
public class TenantBranding {

    public enum ThemePreference { LIGHT, DARK, SYSTEM }

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "logo_dark_url")
    private String logoDarkUrl;

    @Column(name = "favicon_url")
    private String faviconUrl;

    @Column(name = "brand_hue", nullable = false)
    private int brandHue = DesignSystem.DEFAULT_BRAND_HUE;

    @Column(name = "brand_chroma", nullable = false)
    private BigDecimal brandChroma = new BigDecimal(DesignSystem.DEFAULT_BRAND_CHROMA);

    @Column(name = "accent_hue", nullable = false)
    private int accentHue = DesignSystem.DEFAULT_ACCENT_HUE;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_theme", nullable = false)
    private ThemePreference defaultTheme = ThemePreference.SYSTEM;

    @Column(name = "login_headline")
    private String loginHeadline;

    @Column(name = "login_subtext")
    private String loginSubtext;

    @Column(name = "support_email")
    private String supportEmail;

    @Column(name = "email_from_name")
    private String emailFromName;

    @Column(name = "email_footer")
    private String emailFooter;

    @Column(name = "custom_css")
    private String customCss;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public TenantBranding(UUID tenantId) {
        this.tenantId = tenantId;
    }
}
