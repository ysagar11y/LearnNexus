package com.learnnexus.config;

/**
 * The design system's three theming dials, mirrored server-side.
 *
 * <p>These are the same numbers as {@code --brand-h / --brand-c / --accent-h} in
 * {@code design-system/tokens/colors.css}. They are duplicated here because the
 * server renders two surfaces the browser never touches — transactional email
 * and certificate PDFs — and those must match the app a tenant sees. Changing a
 * value here without changing the token file (or the reverse) is a bug.
 */
public final class DesignSystem {

    public static final int DEFAULT_BRAND_HUE = 232;
    public static final String DEFAULT_BRAND_CHROMA = "0.130";
    public static final int DEFAULT_ACCENT_HUE = 38;

    private DesignSystem() {
    }
}
