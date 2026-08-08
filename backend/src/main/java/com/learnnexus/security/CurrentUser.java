package com.learnnexus.security;

import com.learnnexus.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/** Convenience accessors for the authenticated principal. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AppUserPrincipal> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public static AppUserPrincipal require() {
        return find().orElseThrow(() ->
                ApiException.unauthorized("unauthenticated", "Authentication is required."));
    }

    public static UUID requireId() {
        return require().userId();
    }
}
