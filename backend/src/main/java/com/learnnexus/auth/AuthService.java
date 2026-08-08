package com.learnnexus.auth;

import com.learnnexus.audit.AuditService;
import com.learnnexus.common.ApiException;
import com.learnnexus.config.AppProperties;
import com.learnnexus.iam.OrgUnitRepository;
import com.learnnexus.iam.RefreshToken;
import com.learnnexus.iam.RefreshTokenRepository;
import com.learnnexus.iam.User;
import com.learnnexus.iam.UserRepository;
import com.learnnexus.notification.MailService;
import com.learnnexus.security.AppUserPrincipal;
import com.learnnexus.security.CurrentUser;
import com.learnnexus.security.JwtService;
import com.learnnexus.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration RESET_TOKEN_TTL = Duration.ofHours(2);
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final MailService mailService;
    private final RefreshTokenGuard refreshTokenGuard;
    private final AppProperties properties;

    // -----------------------------------------------------------------
    // Sign in
    // -----------------------------------------------------------------

    @Transactional
    public AuthDtos.SessionResponse login(AuthDtos.LoginRequest request, HttpServletRequest httpRequest) {
        TenantContext.Snapshot tenant = TenantContext.require();
        Optional<User> found = userRepository.findByEmail(request.email());

        if (found.isEmpty()) {
            // Run a throwaway hash so a missing account and a wrong password take
            // comparable time, denying an attacker a cheap account-enumeration oracle.
            passwordEncoder.matches(request.password(), "$2a$12$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidinv");
            auditService.recordFor(null, request.email(), AuditService.LOGIN_FAILED,
                    "User", null, "Sign-in attempt for an unknown address", Map.of("reason", "unknown_user"));
            throw invalidCredentials();
        }

        User user = found.get();

        if (user.isLocked()) {
            throw ApiException.unauthorized("account_locked",
                    "Too many failed attempts. Try again in a few minutes.");
        }
        if (user.getStatus() == User.Status.INVITED) {
            throw ApiException.unauthorized("invite_pending",
                    "Finish setting up your account using the invitation link we emailed you.");
        }
        if (!user.canAuthenticate()) {
            throw ApiException.unauthorized("account_inactive", "This account is not active.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user);
            auditService.recordFor(user.getId(), user.getEmail(), AuditService.LOGIN_FAILED,
                    "User", user.getId(), "Incorrect password", Map.of("attempts", user.getFailedLoginAttempts()));
            throw invalidCredentials();
        }

        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        auditService.recordFor(user.getId(), user.getEmail(), AuditService.LOGIN_SUCCEEDED,
                "User", user.getId(), "Signed in", null);

        return issueSession(user, tenant, httpRequest);
    }

    private void registerFailedAttempt(User user) {
        short attempts = (short) (user.getFailedLoginAttempts() + 1);
        user.setFailedLoginAttempts(attempts);
        if (attempts >= User.MAX_FAILED_LOGINS) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
            user.setFailedLoginAttempts((short) 0);
            log.warn("Locked account {} after {} failed attempts", user.getId(), User.MAX_FAILED_LOGINS);
        }
        userRepository.save(user);
    }

    private ApiException invalidCredentials() {
        return ApiException.unauthorized("invalid_credentials", "That email and password combination is not valid.");
    }

    // -----------------------------------------------------------------
    // Token rotation
    // -----------------------------------------------------------------

    @Transactional
    public AuthDtos.SessionResponse refresh(String presentedToken, HttpServletRequest httpRequest) {
        String hash = jwtService.hashToken(presentedToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("invalid_refresh_token",
                        "This session has expired. Please sign in again."));

        if (stored.getRevokedAt() != null) {
            // A revoked token being presented means the token leaked: whoever holds
            // it and the legitimate user both need to be signed out of this family.
            // The revocation must commit in its own transaction, because the
            // exception below would otherwise roll it straight back.
            refreshTokenGuard.revokeFamilyImmediately(stored.getFamilyId());
            throw ApiException.unauthorized("refresh_token_reused",
                    "This session was ended for security reasons. Please sign in again.");
        }
        if (!stored.isUsable()) {
            throw ApiException.unauthorized("invalid_refresh_token",
                    "This session has expired. Please sign in again.");
        }

        User user = userRepository.findActiveById(stored.getUserId())
                .filter(User::canAuthenticate)
                .orElseThrow(() -> ApiException.unauthorized("account_inactive", "This account is not active."));

        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);

        TenantContext.Snapshot tenant = TenantContext.require();
        return issueSession(user, tenant, httpRequest, stored.getFamilyId());
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(jwtService.hashToken(refreshToken)).ifPresent(token -> {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now());
            auditService.recordFor(token.getUserId(), null, AuditService.LOGOUT,
                    "User", token.getUserId(), "Signed out", null);
        });
    }

    @Transactional
    public void logoutEverywhere() {
        UUID userId = CurrentUser.requireId();
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        auditService.record(AuditService.LOGOUT, "User", userId, "Signed out of every device");
    }

    private AuthDtos.SessionResponse issueSession(User user, TenantContext.Snapshot tenant,
                                                  HttpServletRequest httpRequest) {
        return issueSession(user, tenant, httpRequest, UUID.randomUUID());
    }

    private AuthDtos.SessionResponse issueSession(User user, TenantContext.Snapshot tenant,
                                                  HttpServletRequest httpRequest, UUID familyId) {
        AppUserPrincipal principal = AppUserPrincipal.of(user, tenant.slug());
        JwtService.AccessToken accessToken = jwtService.issueAccessToken(principal);

        String refreshValue = jwtService.generateRefreshToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(jwtService.hashToken(refreshValue));
        refreshToken.setFamilyId(familyId);
        refreshToken.setExpiresAt(Instant.now().plus(properties.jwt().refreshTokenTtl()));
        if (httpRequest != null) {
            refreshToken.setUserAgent(trim(httpRequest.getHeader("User-Agent"), 400));
            refreshToken.setIpAddress(AuditService.clientIp(httpRequest));
        }
        refreshTokenRepository.save(refreshToken);

        return new AuthDtos.SessionResponse(
                accessToken.value(),
                refreshValue,
                accessToken.expiresAt(),
                toProfile(user, tenant)
        );
    }

    // -----------------------------------------------------------------
    // Profile
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public AuthDtos.ProfileResponse currentProfile() {
        User user = userRepository.findActiveById(CurrentUser.requireId())
                .orElseThrow(() -> ApiException.unauthorized("account_missing", "This account no longer exists."));
        return toProfile(user, TenantContext.require());
    }

    public AuthDtos.ProfileResponse toProfile(User user, TenantContext.Snapshot tenant) {
        String orgUnitName = user.getOrgUnitId() == null ? null
                : orgUnitRepository.findById(user.getOrgUnitId()).map(unit -> unit.getName()).orElse(null);

        AppUserPrincipal principal = AppUserPrincipal.of(user, tenant.slug());
        return new AuthDtos.ProfileResponse(
                user.getId(),
                tenant.tenantId(),
                tenant.slug(),
                tenant.name(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.displayName(),
                user.getJobTitle(),
                user.getAvatarUrl(),
                user.roleSet(),
                principal.primaryRole(),
                user.getOrgUnitId(),
                orgUnitName,
                user.getLocale(),
                user.getTimezone(),
                user.getLastLoginAt()
        );
    }

    // -----------------------------------------------------------------
    // Password lifecycle
    // -----------------------------------------------------------------

    @Transactional
    public void forgotPassword(String email) {
        TenantContext.Snapshot tenant = TenantContext.require();
        // Always returns quietly: telling a caller whether an address exists is an
        // account-enumeration vulnerability.
        userRepository.findByEmail(email).filter(user -> user.getDeletedAt() == null).ifPresent(user -> {
            String token = jwtService.generateRefreshToken();
            user.setResetTokenHash(jwtService.hashToken(token));
            user.setResetExpiresAt(Instant.now().plus(RESET_TOKEN_TTL));
            userRepository.save(user);

            mailService.sendPasswordReset(user, tenant, token);
            auditService.recordFor(user.getId(), user.getEmail(), AuditService.PASSWORD_RESET_REQUESTED,
                    "User", user.getId(), "Password reset requested", null);
        });
    }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        User user = userRepository.findByResetTokenHash(jwtService.hashToken(request.token()))
                .orElseThrow(() -> ApiException.badRequest("invalid_token",
                        "This reset link is no longer valid. Request a new one."));

        if (user.getResetExpiresAt() == null || user.getResetExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("expired_token",
                    "This reset link has expired. Request a new one.");
        }

        applyNewPassword(user, request.password());
        user.setResetTokenHash(null);
        user.setResetExpiresAt(null);
        userRepository.save(user);

        // A password change invalidates every existing session.
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        auditService.recordFor(user.getId(), user.getEmail(), AuditService.PASSWORD_CHANGED,
                "User", user.getId(), "Password reset completed", null);
    }

    @Transactional
    public AuthDtos.SessionResponse acceptInvite(AuthDtos.AcceptInviteRequest request, HttpServletRequest httpRequest) {
        TenantContext.Snapshot tenant = TenantContext.require();
        User user = userRepository.findByInviteTokenHash(jwtService.hashToken(request.token()))
                .orElseThrow(() -> ApiException.badRequest("invalid_token",
                        "This invitation is no longer valid. Ask your administrator to resend it."));

        if (user.getInviteExpiresAt() == null || user.getInviteExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("expired_token",
                    "This invitation has expired. Ask your administrator to resend it.");
        }

        applyNewPassword(user, request.password());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setStatus(User.Status.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        user.setInviteTokenHash(null);
        user.setInviteExpiresAt(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        auditService.recordFor(user.getId(), user.getEmail(), AuditService.PASSWORD_CHANGED,
                "User", user.getId(), "Invitation accepted", null);

        return issueSession(user, tenant, httpRequest);
    }

    @Transactional
    public void changePassword(AuthDtos.ChangePasswordRequest request) {
        User user = userRepository.findActiveById(CurrentUser.requireId())
                .orElseThrow(() -> ApiException.unauthorized("account_missing", "This account no longer exists."));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("invalid_credentials", "Your current password is not correct.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("password_reused", "Choose a password you have not used before.");
        }

        applyNewPassword(user, request.newPassword());
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        auditService.record(AuditService.PASSWORD_CHANGED, "User", user.getId(), "Password changed");
    }

    private void applyNewPassword(User user, String rawPassword) {
        PasswordPolicy.validate(rawPassword, user.getEmail());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);
        user.setUpdatedAt(Instant.now());
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
