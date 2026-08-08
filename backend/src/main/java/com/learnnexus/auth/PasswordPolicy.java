package com.learnnexus.auth;

import com.learnnexus.common.ApiException;

import java.util.Set;

/**
 * Minimum password rules applied whenever a password is set.
 *
 * <p>Favours length and blocklisting over character-class gymnastics, which push
 * users toward predictable substitutions without adding real entropy.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 200;

    private static final Set<String> COMMON = Set.of(
            "password", "password1", "passw0rd", "12345678", "123456789", "1234567890",
            "qwertyuiop", "letmein123", "welcome123", "admin12345", "iloveyou1",
            "changeme1", "learnnexus", "abc12345", "111111111"
    );

    private PasswordPolicy() {
    }

    public static void validate(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw ApiException.badRequest("weak_password",
                    "Use at least " + MIN_LENGTH + " characters.");
        }
        if (password.length() > MAX_LENGTH) {
            throw ApiException.badRequest("weak_password",
                    "Passwords cannot exceed " + MAX_LENGTH + " characters.");
        }

        String lower = password.toLowerCase();
        if (COMMON.contains(lower)) {
            throw ApiException.badRequest("weak_password",
                    "That password is too common. Choose something less predictable.");
        }
        if (email != null && !email.isBlank()) {
            String localPart = email.split("@")[0].toLowerCase();
            if (localPart.length() >= 4 && lower.contains(localPart)) {
                throw ApiException.badRequest("weak_password",
                        "Your password cannot contain your email address.");
            }
        }
        if (hasSingleCharacterRepeat(password)) {
            throw ApiException.badRequest("weak_password",
                    "Your password cannot be a single repeated character.");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasNonLetter = password.chars().anyMatch(ch -> !Character.isLetter(ch));
        if (!hasLetter || !hasNonLetter) {
            throw ApiException.badRequest("weak_password",
                    "Mix letters with at least one number or symbol.");
        }
    }

    private static boolean hasSingleCharacterRepeat(String password) {
        char first = password.charAt(0);
        return password.chars().allMatch(ch -> ch == first);
    }
}
