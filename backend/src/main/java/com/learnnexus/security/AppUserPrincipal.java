package com.learnnexus.security;

import com.learnnexus.iam.RoleCode;
import com.learnnexus.iam.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated caller. Carries the tenant it was minted for so that every
 * downstream check can compare it against the tenant resolved from the request.
 */
public record AppUserPrincipal(
        UUID userId,
        UUID tenantId,
        String tenantSlug,
        String email,
        String displayName,
        Set<RoleCode> roles
) {

    public static AppUserPrincipal of(User user, String tenantSlug) {
        return new AppUserPrincipal(
                user.getId(),
                user.getTenantId(),
                tenantSlug,
                user.getEmail(),
                user.displayName(),
                user.roleSet()
        );
    }

    public Collection<GrantedAuthority> authorities() {
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.authority()))
                .toList();
    }

    public boolean hasRole(RoleCode role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(RoleCode... candidates) {
        for (RoleCode candidate : candidates) {
            if (roles.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** The most privileged role held, used to choose a default landing page. */
    public RoleCode primaryRole() {
        return roles.stream().min(Comparator.comparingInt(RoleCode::rank)).orElse(RoleCode.LEARNER);
    }

    public boolean isTenantAdmin() {
        return hasAnyRole(RoleCode.TENANT_ADMIN, RoleCode.PLATFORM_ADMIN);
    }
}
