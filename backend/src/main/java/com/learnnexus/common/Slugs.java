package com.learnnexus.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** URL-safe slug generation. */
public final class Slugs {

    private Slugs() {
    }

    public static String of(String input) {
        if (input == null || input.isBlank()) {
            return "untitled";
        }
        String normalised = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (normalised.isBlank()) {
            return "untitled";
        }
        return normalised.length() > 200 ? normalised.substring(0, 200) : normalised;
    }

    /**
     * Appends {@code -2}, {@code -3}, … until {@code isTaken} says the slug is free.
     * Callers still need the database's unique index: this only avoids the common
     * collision, it does not make concurrent inserts safe on its own.
     */
    public static String unique(String input, Predicate<String> isTaken) {
        String base = of(input);
        if (!isTaken.test(base)) {
            return base;
        }
        for (int suffix = 2; suffix < 500; suffix++) {
            String candidate = base + "-" + suffix;
            if (!isTaken.test(candidate)) {
                return candidate;
            }
        }
        return base + "-" + System.currentTimeMillis();
    }
}
