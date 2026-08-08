package com.learnnexus.tenancy;

import com.learnnexus.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves the acting tenant before anything else touches the request, so that
 * authentication, authorisation and every subsequent query all agree on scope.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>the tenant override header — used by the SPA in local development and by
 *       server-to-server callers that share one host name;</li>
 *   <li>an exact custom-domain match, e.g. {@code learn.acme.com};</li>
 *   <li>a sub-domain of the configured root domain, e.g. {@code acme.learnnexus.app}.</li>
 * </ol>
 *
 * <p>Failing to resolve is not an error here. Endpoints that need a tenant assert
 * it themselves via {@link TenantContext#require()}; genuinely global endpoints
 * (health, certificate verification, OpenAPI) must keep working without one.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final TenantDirectory directory;
    private final AppProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            resolve(request).ifPresent(snapshot -> {
                TenantContext.set(snapshot);
                response.setHeader("X-Resolved-Tenant", snapshot.slug());
            });
            chain.doFilter(request, response);
        } finally {
            // The container reuses request threads; leaking a tenant across
            // requests would be the single most dangerous bug in this system.
            TenantContext.clear();
        }
    }

    private Optional<TenantContext.Snapshot> resolve(HttpServletRequest request) {
        String headerName = properties.tenancy().headerOverride();
        if (StringUtils.hasText(headerName)) {
            String headerValue = request.getHeader(headerName);
            if (StringUtils.hasText(headerValue)) {
                Optional<TenantContext.Snapshot> bySlug = directory.findBySlug(normalise(headerValue));
                if (bySlug.isPresent()) {
                    return bySlug;
                }
                log.debug("Tenant header '{}' did not match a known tenant", headerValue);
            }
        }

        String host = hostOf(request);
        if (!StringUtils.hasText(host)) {
            return Optional.empty();
        }

        Optional<TenantContext.Snapshot> byDomain = directory.findByCustomDomain(host);
        if (byDomain.isPresent()) {
            return byDomain;
        }

        return subdomainOf(host).flatMap(directory::findBySlug);
    }

    private Optional<String> subdomainOf(String host) {
        String root = properties.tenancy().rootDomain();
        if (!StringUtils.hasText(root) || !host.endsWith("." + root)) {
            return Optional.empty();
        }
        String candidate = host.substring(0, host.length() - root.length() - 1);
        // Only a single label is a tenant slug; deeper names are not addresses we own.
        return candidate.contains(".") || candidate.isBlank()
                ? Optional.empty()
                : Optional.of(candidate);
    }

    private String hostOf(HttpServletRequest request) {
        String host = request.getHeader("X-Forwarded-Host");
        if (!StringUtils.hasText(host)) {
            host = request.getServerName();
        }
        if (!StringUtils.hasText(host)) {
            return "";
        }
        // Strip any port and take the left-most entry of a proxy chain.
        int comma = host.indexOf(',');
        if (comma > -1) {
            host = host.substring(0, comma);
        }
        int colon = host.indexOf(':');
        if (colon > -1) {
            host = host.substring(0, colon);
        }
        return normalise(host);
    }

    private String normalise(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
