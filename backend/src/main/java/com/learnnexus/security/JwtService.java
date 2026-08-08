package com.learnnexus.security;

import com.learnnexus.common.ApiException;
import com.learnnexus.config.AppProperties;
import com.learnnexus.iam.RoleCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Issues and verifies access tokens, and mints the opaque refresh tokens whose
 * hashes are persisted by {@code RefreshToken}.
 */
@Slf4j
@Service
public class JwtService {

    private static final String CLAIM_TENANT = "tid";
    private static final String CLAIM_TENANT_SLUG = "tsl";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_EMAIL = "email";

    private final SecretKey signingKey;
    private final AppProperties properties;
    private final SecureRandom random = new SecureRandom();

    public JwtService(AppProperties properties) {
        this.properties = properties;
        byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes to sign HS256 tokens safely.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret);
    }

    public record AccessToken(String value, Instant expiresAt) {}

    public AccessToken issueAccessToken(AppUserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.jwt().accessTokenTtl());
        String token = Jwts.builder()
                .issuer(properties.jwt().issuer())
                .subject(principal.userId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(CLAIM_TENANT, principal.tenantId().toString())
                .claim(CLAIM_TENANT_SLUG, principal.tenantSlug())
                .claim(CLAIM_EMAIL, principal.email())
                .claim(CLAIM_NAME, principal.displayName())
                .claim(CLAIM_ROLES, principal.roles().stream().map(Enum::name).toList())
                .signWith(signingKey)
                .compact();
        return new AccessToken(token, expiry);
    }

    public AppUserPrincipal parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.jwt().issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            @SuppressWarnings("unchecked")
            List<String> rawRoles = claims.get(CLAIM_ROLES, List.class);
            Set<RoleCode> roles = rawRoles == null
                    ? Set.of(RoleCode.LEARNER)
                    : rawRoles.stream().map(RoleCode::valueOf).collect(Collectors.toUnmodifiableSet());

            return new AppUserPrincipal(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get(CLAIM_TENANT, String.class)),
                    claims.get(CLAIM_TENANT_SLUG, String.class),
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_NAME, String.class),
                    roles
            );
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected access token: {}", ex.getMessage());
            throw ApiException.unauthorized("invalid_token", "The access token is invalid or has expired.");
        }
    }

    /**
     * Refresh tokens are opaque random strings rather than JWTs: they must be
     * revocable, and only their hash is ever stored.
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
