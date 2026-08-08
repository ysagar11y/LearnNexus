package com.learnnexus.auth;

import com.learnnexus.AbstractIntegrationTest;
import com.learnnexus.iam.RoleCode;
import com.learnnexus.iam.User;
import com.learnnexus.iam.UserRepository;
import com.learnnexus.tenancy.TenantContext;
import com.learnnexus.tenant.Tenant;
import com.learnnexus.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end authentication behaviour over real HTTP, including the two
 * properties that matter most in a multi-tenant system: a token is useless
 * against another tenant, and a replayed refresh token kills the session family.
 */
@DisplayName("Authentication")
class AuthenticationIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-9";

    @Autowired TestRestTemplate rest;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Value("${local.server.port}") int port;

    private String slugOne;
    private String slugTwo;

    @BeforeEach
    void seed() {
        slugOne = "one-" + UUID.randomUUID().toString().substring(0, 8);
        slugTwo = "two-" + UUID.randomUUID().toString().substring(0, 8);

        var tenantOne = createTenant(slugOne);
        var tenantTwo = createTenant(slugTwo);

        createUser(tenantOne, "user@one.test");
        createUser(tenantTwo, "user@two.test");
    }

    @Test
    @DisplayName("valid credentials return a session for the resolved tenant")
    void loginSucceeds() {
        var response = login(slugOne, "user@one.test", PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys("accessToken", "refreshToken", "user");

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) response.getBody().get("user");
        assertThat(user).containsEntry("tenantSlug", slugOne);
    }

    @Test
    @DisplayName("a wrong password is rejected without revealing whether the account exists")
    void wrongPasswordIsRejected() {
        var wrongPassword = login(slugOne, "user@one.test", "not-the-password");
        var unknownAccount = login(slugOne, "nobody@one.test", PASSWORD);

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownAccount.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Identical code and message: the response must not distinguish the two.
        assertThat(wrongPassword.getBody().get("code")).isEqualTo(unknownAccount.getBody().get("code"));
        assertThat(wrongPassword.getBody().get("message")).isEqualTo(unknownAccount.getBody().get("message"));
    }

    @Test
    @DisplayName("a user cannot sign in to a tenant they do not belong to")
    void credentialsDoNotWorkAcrossTenants() {
        var response = login(slugTwo, "user@one.test", PASSWORD);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("an access token minted for one tenant is refused on another")
    void accessTokenIsBoundToItsTenant() {
        String token = (String) login(slugOne, "user@one.test", PASSWORD).getBody().get("accessToken");

        var ownTenant = get("/api/v1/auth/me", slugOne, token);
        assertThat(ownTenant.getStatusCode()).isEqualTo(HttpStatus.OK);

        var otherTenant = get("/api/v1/auth/me", slugTwo, token);
        assertThat(otherTenant.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(otherTenant.getBody()).containsEntry("code", "tenant_mismatch");
    }

    @Test
    @DisplayName("refreshing rotates the token, and replaying the old one ends the session family")
    void refreshTokensRotateAndDetectReuse() {
        var session = login(slugOne, "user@one.test", PASSWORD).getBody();
        String firstRefresh = (String) session.get("refreshToken");

        var rotated = post("/api/v1/auth/refresh", slugOne, Map.of("refreshToken", firstRefresh));
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);

        String secondRefresh = (String) rotated.getBody().get("refreshToken");
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        // Replaying the rotated-out token is the signature of a stolen token.
        var replayed = post("/api/v1/auth/refresh", slugOne, Map.of("refreshToken", firstRefresh));
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(replayed.getBody()).containsEntry("code", "refresh_token_reused");

        // …and the whole family is revoked, including the token the thief rotated to.
        var afterRevocation = post("/api/v1/auth/refresh", slugOne, Map.of("refreshToken", secondRefresh));
        assertThat(afterRevocation.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("protected endpoints reject anonymous callers")
    void protectedEndpointsRequireAuthentication() {
        var response = get("/api/v1/users", slugOne, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a learner cannot reach the platform console")
    void roleBoundariesAreEnforced() {
        String token = (String) login(slugOne, "user@one.test", PASSWORD).getBody().get("accessToken");
        var response = get("/api/v1/platform/overview", slugOne, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("password reset never reveals whether an address is registered")
    void forgotPasswordIsAlwaysAccepted() {
        var known = post("/api/v1/auth/forgot-password", slugOne, Map.of("email", "user@one.test"));
        var unknown = post("/api/v1/auth/forgot-password", slugOne, Map.of("email", "ghost@one.test"));

        assertThat(known.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    // -----------------------------------------------------------------

    private ResponseEntity<Map> login(String slug, String email, String password) {
        return post("/api/v1/auth/login", slug, Map.of("email", email, "password", password));
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> post(String path, String slug, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant", slug);
        return rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> get(String path, String slug, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant", slug);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private TenantContext.Snapshot createTenant(String slug) {
        return transactionTemplate.execute(status -> {
            Tenant tenant = new Tenant(UUID.randomUUID(), slug, slug.toUpperCase());
            tenant.setStatus(Tenant.Status.ACTIVE);
            tenantRepository.save(tenant);
            return new TenantContext.Snapshot(tenant.getId(), slug, tenant.getName(), false);
        });
    }

    private void createUser(TenantContext.Snapshot tenant, String email) {
        asTenant(tenant, () -> {
            User user = new User();
            user.setEmail(email);
            user.setFirstName("Test");
            user.setStatus(User.Status.ACTIVE);
            user.setPasswordHash(passwordEncoder.encode(PASSWORD));
            user.setRoleSet(Set.of(RoleCode.LEARNER));
            return userRepository.save(user);
        });
    }
}
