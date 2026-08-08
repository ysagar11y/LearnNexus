package com.learnnexus.iam;

import com.learnnexus.tenancy.TenantScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends TenantScoped {

    public enum Status { INVITED, ACTIVE, SUSPENDED }

    /** Consecutive failures tolerated before the account is temporarily locked. */
    public static final int MAX_FAILED_LOGINS = 5;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "job_title")
    private String jobTitle;

    private String phone;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.INVITED;

    @Column(name = "org_unit_id")
    private UUID orgUnitId;

    @Column(name = "manager_id")
    private UUID managerId;

    private String locale;

    private String timezone;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "roles", nullable = false)
    private String[] roles = {RoleCode.LEARNER.name()};

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    @Column(name = "failed_login_attempts", nullable = false)
    private short failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "invite_token_hash")
    private String inviteTokenHash;

    @Column(name = "invite_expires_at")
    private Instant inviteExpiresAt;

    @Column(name = "reset_token_hash")
    private String resetTokenHash;

    @Column(name = "reset_expires_at")
    private Instant resetExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Set<RoleCode> roleSet() {
        return RoleCode.parse(roles);
    }

    public void setRoleSet(Set<RoleCode> newRoles) {
        this.roles = RoleCode.toArray(newRoles.isEmpty() ? Set.of(RoleCode.LEARNER) : newRoles);
    }

    public boolean hasRole(RoleCode role) {
        return roleSet().contains(role);
    }

    public String displayName() {
        return lastName == null || lastName.isBlank() ? firstName : firstName + " " + lastName;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /**
     * Credentials are only usable by an active account that is not serving a
     * lockout; invited users must complete their invitation first.
     */
    public boolean canAuthenticate() {
        return deletedAt == null && status == Status.ACTIVE && passwordHash != null && !isLocked();
    }
}
