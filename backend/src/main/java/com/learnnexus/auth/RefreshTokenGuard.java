package com.learnnexus.auth;

import com.learnnexus.iam.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Revokes a refresh-token family in its own transaction.
 *
 * <p>This exists to fix a specific, security-relevant ordering problem. When a
 * already-rotated refresh token is presented again — the signature of a stolen
 * token — the correct response is to revoke the entire family and reject the
 * request. Doing both inside one transaction does not work: the rejection is an
 * exception, the exception rolls the transaction back, and the revocation is
 * undone. The attacker's token would keep working.
 *
 * <p>{@link Propagation#REQUIRES_NEW} commits the revocation before the caller
 * throws, so the family really is dead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenGuard {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamilyImmediately(UUID familyId) {
        int revoked = refreshTokenRepository.revokeFamily(familyId, Instant.now());
        log.warn("Revoked {} refresh tokens in family {} after a replay", revoked, familyId);
    }
}
