package com.learnnexus.audit;

import com.learnnexus.security.AppUserPrincipal;
import com.learnnexus.security.CurrentUser;
import com.learnnexus.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * Records administrative and security-relevant actions.
 *
 * <p>Writes participate in the caller's transaction on purpose: if the action is
 * rolled back it did not happen, and logging it anyway would make the trail lie.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    /** Stable action names; used for filtering in the admin console. */
    public static final String LOGIN_SUCCEEDED = "auth.login.succeeded";
    public static final String LOGIN_FAILED = "auth.login.failed";
    public static final String LOGOUT = "auth.logout";
    public static final String PASSWORD_RESET_REQUESTED = "auth.password.reset_requested";
    public static final String PASSWORD_CHANGED = "auth.password.changed";
    public static final String USER_CREATED = "user.created";
    public static final String USER_UPDATED = "user.updated";
    public static final String USER_DEACTIVATED = "user.deactivated";
    public static final String USER_ROLES_CHANGED = "user.roles_changed";
    public static final String COURSE_CREATED = "course.created";
    public static final String COURSE_UPDATED = "course.updated";
    public static final String COURSE_PUBLISHED = "course.published";
    public static final String COURSE_ARCHIVED = "course.archived";
    public static final String ENROLLMENT_CREATED = "enrollment.created";
    public static final String ENROLLMENT_WITHDRAWN = "enrollment.withdrawn";
    public static final String CERTIFICATE_ISSUED = "certificate.issued";
    public static final String CERTIFICATE_REVOKED = "certificate.revoked";
    public static final String ATTEMPT_GRADED = "assessment.attempt_graded";
    public static final String BRANDING_UPDATED = "tenant.branding_updated";
    public static final String SETTINGS_UPDATED = "tenant.settings_updated";
    public static final String TENANT_CREATED = "platform.tenant_created";
    public static final String TENANT_STATUS_CHANGED = "platform.tenant_status_changed";
    public static final String TENANT_IMPERSONATED = "platform.tenant_impersonated";

    private final AuditLogRepository repository;

    @Transactional
    public void record(String action, String entityType, Object entityId, String summary) {
        record(action, entityType, entityId, summary, null);
    }

    @Transactional
    public void record(String action, String entityType, Object entityId, String summary,
                       Map<String, Object> metadata) {
        AppUserPrincipal actor = CurrentUser.find().orElse(null);
        write(action, entityType, entityId, summary, metadata,
                actor == null ? null : actor.userId(),
                actor == null ? null : actor.email());
    }

    /**
     * Records an action for a known actor even when no security context exists —
     * a failed login, for instance, happens before authentication succeeds.
     */
    @Transactional
    public void recordFor(UUID actorId, String actorEmail, String action, String entityType,
                          Object entityId, String summary, Map<String, Object> metadata) {
        write(action, entityType, entityId, summary, metadata, actorId, actorEmail);
    }

    private void write(String action, String entityType, Object entityId, String summary,
                       Map<String, Object> metadata, UUID actorId, String actorEmail) {
        if (TenantContext.current().isEmpty()) {
            // Nothing to attribute the entry to; dropping it is preferable to
            // writing an entry that no tenant can ever read back.
            log.debug("Skipping audit entry '{}' with no tenant context", action);
            return;
        }

        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId == null ? null : entityId.toString());
        entry.setSummary(truncate(summary, 500));
        entry.setMetadata(metadata);
        entry.setActorId(actorId);
        entry.setActorEmail(actorEmail);

        HttpServletRequest request = currentRequest();
        if (request != null) {
            entry.setIpAddress(clientIp(request));
            entry.setUserAgent(truncate(request.getHeader("User-Agent"), 400));
        }

        repository.save(entry);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > -1 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
