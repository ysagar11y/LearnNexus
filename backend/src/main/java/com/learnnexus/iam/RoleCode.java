package com.learnnexus.iam;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The closed set of roles a user may hold. Mirrors the {@code users_roles_chk}
 * constraint and the {@code roles} reference table.
 */
public enum RoleCode {

    /** Operates the whole platform; only ever granted inside the system tenant. */
    PLATFORM_ADMIN(0),
    TENANT_ADMIN(1),
    AUTHOR(2),
    INSTRUCTOR(3),
    MANAGER(4),
    LEARNER(5);

    /** Spring Security prefixes authorities with {@code ROLE_} by convention. */
    public static final String AUTHORITY_PREFIX = "ROLE_";

    private final int rank;

    RoleCode(int rank) {
        this.rank = rank;
    }

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    /** Lower is more privileged; used to pick a user's landing area after login. */
    public int rank() {
        return rank;
    }

    public static Set<RoleCode> parse(String[] codes) {
        if (codes == null) {
            return Set.of(LEARNER);
        }
        return Arrays.stream(codes)
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(RoleCode::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static String[] toArray(Set<RoleCode> roles) {
        return roles.stream().map(Enum::name).toArray(String[]::new);
    }
}
