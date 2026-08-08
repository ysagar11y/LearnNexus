package com.learnnexus.security;

import com.learnnexus.common.ApiException;
import com.learnnexus.tenancy.TenantContext;
import com.learnnexus.tenancy.TenantDirectory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Turns a bearer token into an authenticated {@link AppUserPrincipal}, and — the
 * part that matters for a multi-tenant system — refuses to let a token issued
 * for one tenant act on another.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final TenantDirectory tenantDirectory;
    private final SecurityProblemWriter problemWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            AppUserPrincipal principal = jwtService.parseAccessToken(header.substring(BEARER.length()).trim());

            TenantContext.Snapshot resolved = TenantContext.current().orElse(null);
            if (resolved == null) {
                // No tenant could be derived from the host or header (the common case
                // for API clients hitting a shared host). Adopt the token's tenant,
                // which is signed and therefore trustworthy.
                TenantContext.Snapshot fromToken = tenantDirectory.findById(principal.tenantId())
                        .orElseThrow(() -> ApiException.unauthorized(
                                "tenant_unavailable", "The tenant for this token is no longer active."));
                TenantContext.set(fromToken);
            } else if (!resolved.tenantId().equals(principal.tenantId())) {
                // A valid token presented against a different tenant's portal. This is
                // the exact shape of a cross-tenant escalation attempt, so it is refused
                // rather than silently re-scoped.
                log.warn("Rejected token for tenant {} presented on tenant {}",
                        principal.tenantId(), resolved.tenantId());
                throw ApiException.unauthorized("tenant_mismatch",
                        "This session does not belong to the requested workspace.");
            }

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.authorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            chain.doFilter(request, response);
        } catch (ApiException ex) {
            SecurityContextHolder.clearContext();
            problemWriter.write(response, ex);
        }
    }
}
