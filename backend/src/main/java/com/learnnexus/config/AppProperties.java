package com.learnnexus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Strongly-typed view of the {@code app.*} configuration namespace.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Storage storage,
        Mail mail,
        Tenancy tenancy,
        Seed seed,
        String publicBaseUrl,
        List<String> corsOrigins
) {

    public record Jwt(
            String secret,
            String issuer,
            Duration accessTokenTtl,
            Duration refreshTokenTtl
    ) {}

    public record Storage(
            String endpoint,
            String publicEndpoint,
            String region,
            String bucket,
            String accessKey,
            String secretKey,
            boolean pathStyleAccess,
            Duration presignTtl,
            long maxUploadBytes
    ) {}

    public record Mail(
            String fromAddress,
            String fromName,
            boolean enabled
    ) {}

    /**
     * @param rootDomain      apex domain used to derive a tenant from a host header,
     *                        e.g. {@code learnnexus.app} resolves {@code acme.learnnexus.app} to "acme".
     * @param systemSlug      slug of the reserved tenant that hosts platform super-admins.
     * @param headerOverride  header consulted before the host name; intended for local
     *                        development and server-to-server calls.
     */
    public record Tenancy(
            String rootDomain,
            String systemSlug,
            String headerOverride
    ) {}

    public record Seed(
            boolean enabled,
            String demoPassword
    ) {}
}
