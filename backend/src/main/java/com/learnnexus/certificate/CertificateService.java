package com.learnnexus.certificate;

import com.learnnexus.audit.AuditService;
import com.learnnexus.catalog.CatalogRepositories;
import com.learnnexus.catalog.Course;
import com.learnnexus.common.ApiException;
import com.learnnexus.common.PageResponse;
import com.learnnexus.config.AppProperties;
import com.learnnexus.config.DesignSystem;
import com.learnnexus.enrollment.CertificateIssuer;
import com.learnnexus.iam.RoleCode;
import com.learnnexus.iam.User;
import com.learnnexus.iam.UserRepository;
import com.learnnexus.media.StorageService;
import com.learnnexus.notification.MailService;
import com.learnnexus.notification.NotificationService;
import com.learnnexus.security.AppUserPrincipal;
import com.learnnexus.security.CurrentUser;
import com.learnnexus.tenancy.TenantContext;
import com.learnnexus.tenant.TenantBranding;
import com.learnnexus.tenant.TenantBrandingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateService implements CertificateIssuer {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 20;

    private final CertificateRepositories.CertificateRepository certificateRepository;
    private final CertificateRepositories.CertificateTemplateRepository templateRepository;
    private final CatalogRepositories.CourseRepository courseRepository;
    private final com.learnnexus.enrollment.EnrollmentRepositories.EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final TenantBrandingRepository brandingRepository;
    private final CertificateRenderer renderer;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final AuditService auditService;
    private final AppProperties properties;
    private final com.learnnexus.common.TenantAwareJdbc jdbc;

    private final SecureRandom random = new SecureRandom();

    // =================================================================
    // Issuing
    // =================================================================

    @Override
    @Transactional
    public Optional<UUID> issueFor(UUID enrollmentId) {
        var existing = certificateRepository.findForEnrollment(enrollmentId);
        if (existing.isPresent()) {
            return Optional.of(existing.get().getId());
        }

        var enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> ApiException.notFound("Enrolment", enrollmentId));

        Course course = courseRepository.findActiveById(enrollment.getCourseId())
                .orElseThrow(() -> ApiException.notFound("Course", enrollment.getCourseId()));
        if (!course.isCertificateEnabled()) {
            return Optional.empty();
        }

        User learner = userRepository.findActiveById(enrollment.getUserId())
                .orElseThrow(() -> ApiException.notFound("User", enrollment.getUserId()));

        CertificateTemplate template = resolveTemplate(course.getCertificateTemplateId());

        Certificate certificate = new Certificate();
        certificate.setUserId(learner.getId());
        certificate.setCourseId(course.getId());
        certificate.setEnrollmentId(enrollmentId);
        certificate.setTemplateId(template == null ? null : template.getId());
        certificate.setSerialNumber(nextSerialNumber());
        certificate.setVerificationCode(newVerificationCode());
        certificate.setRecipientName(learner.displayName());
        certificate.setCourseTitle(course.getTitle());
        certificate.setScore(bestScoreFor(course.getId(), learner.getId()));
        certificate.setIssuedAt(Instant.now());

        if (template != null && template.getValidityMonths() != null) {
            certificate.setExpiresAt(Instant.now().plus(template.getValidityMonths() * 30L, ChronoUnit.DAYS));
        }
        certificateRepository.save(certificate);

        // Rendering is best-effort at issue time: a template that fails to lay out
        // must not block course completion. The download endpoint renders on demand.
        try {
            storePdf(certificate, template);
        } catch (RuntimeException ex) {
            log.error("Deferred PDF generation for certificate {}: {}", certificate.getId(), ex.getMessage());
        }

        TenantContext.Snapshot tenant = TenantContext.require();
        notificationService.certificateIssued(learner.getId(), course.getTitle());
        mailService.sendCertificateIssued(learner, tenant, course.getTitle(), certificate.getVerificationCode());
        auditService.record(AuditService.CERTIFICATE_ISSUED, "Certificate", certificate.getId(),
                "Issued certificate for " + course.getTitle() + " to " + learner.getEmail());

        return Optional.of(certificate.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findForEnrollment(UUID enrollmentId) {
        return certificateRepository.findForEnrollment(enrollmentId).map(Certificate::getId);
    }

    private BigDecimal bestScoreFor(UUID courseId, UUID userId) {
        return jdbc.queryOne("""
                with scoped as (
                    select id from assessments where tenant_id = ? and course_id = ?
                )
                select max(a.percentage) as best
                from attempts a
                join scoped s on s.id = a.assessment_id
                where a.user_id = ? and a.status in ('SUBMITTED','GRADED')
                """, (rs, rowNum) -> rs.getBigDecimal("best"), courseId, userId)
                .orElse(null);
    }

    private CertificateTemplate resolveTemplate(UUID preferredId) {
        if (preferredId != null) {
            var preferred = templateRepository.findById(preferredId);
            if (preferred.isPresent()) {
                return preferred.get();
            }
        }
        return templateRepository.findDefault()
                .or(() -> templateRepository.findAllOrdered().stream().findFirst())
                .orElse(null);
    }

    private String nextSerialNumber() {
        // Human-readable and sortable: LN-<year>-<8 random chars>. The uniqueness
        // check spans tenants because the serial is printed on the certificate.
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "LN-%d-%s".formatted(
                    Instant.now().atZone(java.time.ZoneOffset.UTC).getYear(), randomCode(8));
            if (certificateRepository.countBySerialAcrossTenants(candidate) == 0) {
                return candidate;
            }
        }
        throw ApiException.unprocessable("serial_generation_failed",
                "Could not allocate a certificate number. Please try again.");
    }

    private String newVerificationCode() {
        return randomCode(CODE_LENGTH);
    }

    private String randomCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return builder.toString();
    }

    // =================================================================
    // PDF
    // =================================================================

    private void storePdf(Certificate certificate, CertificateTemplate template) {
        byte[] pdf = renderPdf(certificate, template);
        String key = storageService.buildKey("certificates", certificate.getSerialNumber() + ".pdf");
        storageService.put(key, pdf, "application/pdf");
        certificate.setPdfKey(key);
        certificateRepository.save(certificate);
    }

    private byte[] renderPdf(Certificate certificate, CertificateTemplate template) {
        TenantContext.Snapshot tenant = TenantContext.require();
        TenantBranding branding = brandingRepository.findById(tenant.tenantId()).orElse(null);

        String html = template == null ? CertificateRenderer.defaultTemplate() : template.getHtmlTemplate();
        var orientation = template == null
                ? CertificateTemplate.Orientation.LANDSCAPE : template.getOrientation();

        var fields = CertificateRenderer.fieldsFor(
                certificate,
                tenant.name(),
                branding == null ? null : branding.getLogoUrl(),
                branding == null ? DesignSystem.DEFAULT_BRAND_HUE : branding.getBrandHue(),
                properties.publicBaseUrl() + "/verify/" + certificate.getVerificationCode());

        return renderer.render(html, fields, orientation);
    }

    @Transactional
    public byte[] download(UUID certificateId) {
        Certificate certificate = requireVisible(certificateId);
        CertificateTemplate template = certificate.getTemplateId() == null
                ? resolveTemplate(null)
                : templateRepository.findById(certificate.getTemplateId()).orElseGet(() -> resolveTemplate(null));
        return renderPdf(certificate, template);
    }

    // =================================================================
    // Reads
    // =================================================================

    public record CertificateView(
            UUID id,
            UUID courseId,
            String courseTitle,
            String recipientName,
            String serialNumber,
            String verificationCode,
            String verificationUrl,
            BigDecimal score,
            Instant issuedAt,
            Instant expiresAt,
            boolean valid,
            boolean expired,
            boolean revoked,
            String revokedReason
    ) {}

    /** What the public verification page is allowed to see. */
    public record VerificationResult(
            boolean valid,
            String status,
            String recipientName,
            String courseTitle,
            String issuerName,
            String serialNumber,
            Instant issuedAt,
            Instant expiresAt
    ) {}

    @Transactional(readOnly = true)
    public List<CertificateView> myCertificates() {
        return certificateRepository.findForUser(CurrentUser.requireId()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<CertificateView> search(UUID courseId, UUID userId, int page, int size) {
        var results = certificateRepository.search(courseId, userId,
                PageRequest.of(page, Math.min(size, 100)));
        return PageResponse.of(results, this::toView);
    }

    @Transactional(readOnly = true)
    public CertificateView get(UUID certificateId) {
        return toView(requireVisible(certificateId));
    }

    /**
     * Public verification. Returns only what a third party needs to confirm the
     * credential, and never reveals whether a code simply does not exist versus
     * belongs to another workspace.
     */
    @Transactional(readOnly = true)
    public VerificationResult verify(String code) {
        String normalised = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        Certificate certificate = certificateRepository
                .findByVerificationCodeAcrossTenants(normalised)
                .orElseThrow(() -> ApiException.notFound(
                        "No certificate matches that code."));

        String issuer = jdbc.unscoped().query(
                        "select name from tenants where id = ?",
                        (rs, rowNum) -> rs.getString("name"), certificate.getTenantId())
                .stream().findFirst().orElse("Unknown issuer");

        String status = certificate.getRevokedAt() != null ? "REVOKED"
                : certificate.isExpired() ? "EXPIRED" : "VALID";

        return new VerificationResult(
                certificate.isValid(), status, certificate.getRecipientName(), certificate.getCourseTitle(),
                issuer, certificate.getSerialNumber(), certificate.getIssuedAt(), certificate.getExpiresAt());
    }

    @Transactional
    public CertificateView revoke(UUID certificateId, String reason) {
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> ApiException.notFound("Certificate", certificateId));
        if (certificate.getRevokedAt() != null) {
            return toView(certificate);
        }
        certificate.setRevokedAt(Instant.now());
        certificate.setRevokedReason(reason);
        certificateRepository.save(certificate);

        auditService.record(AuditService.CERTIFICATE_REVOKED, "Certificate", certificateId,
                "Revoked certificate " + certificate.getSerialNumber(),
                java.util.Map.of("reason", reason == null ? "" : reason));
        return toView(certificate);
    }

    private Certificate requireVisible(UUID certificateId) {
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> ApiException.notFound("Certificate", certificateId));

        AppUserPrincipal principal = CurrentUser.require();
        boolean privileged = principal.hasAnyRole(
                RoleCode.TENANT_ADMIN, RoleCode.PLATFORM_ADMIN, RoleCode.INSTRUCTOR, RoleCode.MANAGER);
        if (!privileged && !certificate.getUserId().equals(principal.userId())) {
            throw ApiException.forbidden("This certificate belongs to someone else.");
        }
        return certificate;
    }

    private CertificateView toView(Certificate certificate) {
        return new CertificateView(
                certificate.getId(), certificate.getCourseId(), certificate.getCourseTitle(),
                certificate.getRecipientName(), certificate.getSerialNumber(), certificate.getVerificationCode(),
                properties.publicBaseUrl() + "/verify/" + certificate.getVerificationCode(),
                certificate.getScore(), certificate.getIssuedAt(), certificate.getExpiresAt(),
                certificate.isValid(), certificate.isExpired(), certificate.getRevokedAt() != null,
                certificate.getRevokedReason());
    }

    // =================================================================
    // Templates
    // =================================================================

    public record TemplateView(
            UUID id,
            String name,
            String htmlTemplate,
            CertificateTemplate.Orientation orientation,
            Integer validityMonths,
            boolean defaultTemplate,
            Instant updatedAt
    ) {}

    public record TemplateRequest(
            String name,
            String htmlTemplate,
            CertificateTemplate.Orientation orientation,
            Integer validityMonths,
            boolean defaultTemplate
    ) {}

    @Transactional(readOnly = true)
    public List<TemplateView> templates() {
        return templateRepository.findAllOrdered().stream().map(this::toTemplateView).toList();
    }

    @Transactional
    public TemplateView saveTemplate(UUID templateId, TemplateRequest request) {
        CertificateTemplate template = templateId == null
                ? new CertificateTemplate()
                : templateRepository.findById(templateId)
                        .orElseThrow(() -> ApiException.notFound("Certificate template", templateId));

        template.setName(request.name() == null || request.name().isBlank()
                ? "Untitled template" : request.name().trim());
        template.setHtmlTemplate(request.htmlTemplate() == null || request.htmlTemplate().isBlank()
                ? CertificateRenderer.defaultTemplate() : request.htmlTemplate());
        template.setOrientation(request.orientation() == null
                ? CertificateTemplate.Orientation.LANDSCAPE : request.orientation());
        template.setValidityMonths(request.validityMonths());
        template.setUpdatedAt(Instant.now());

        if (request.defaultTemplate()) {
            // The partial unique index permits only one default per tenant, so the
            // previous one has to be cleared before this one is saved.
            templateRepository.findDefault().ifPresent(current -> {
                if (!current.getId().equals(template.getId())) {
                    current.setDefaultTemplate(false);
                    templateRepository.save(current);
                }
            });
        }
        template.setDefaultTemplate(request.defaultTemplate());
        templateRepository.save(template);

        return toTemplateView(template);
    }

    /** Renders a template with placeholder data so an author can preview a design. */
    @Transactional(readOnly = true)
    public byte[] previewTemplate(TemplateRequest request) {
        TenantContext.Snapshot tenant = TenantContext.require();
        TenantBranding branding = brandingRepository.findById(tenant.tenantId()).orElse(null);

        var fields = new CertificateRenderer.Fields(
                CurrentUser.require().displayName(),
                "Advanced Distributed Systems",
                tenant.name(),
                "12 March 2026",
                "12 March 2028",
                "94%",
                "LN-2026-PREVIEW",
                "PREVIEWCODE1234ABCD",
                properties.publicBaseUrl() + "/verify/PREVIEWCODE1234ABCD",
                branding == null ? null : branding.getLogoUrl(),
                branding == null ? DesignSystem.DEFAULT_BRAND_HUE : branding.getBrandHue());

        String html = request.htmlTemplate() == null || request.htmlTemplate().isBlank()
                ? CertificateRenderer.defaultTemplate() : request.htmlTemplate();
        var orientation = request.orientation() == null
                ? CertificateTemplate.Orientation.LANDSCAPE : request.orientation();

        return renderer.render(html, fields, orientation);
    }

    @Transactional
    public void deleteTemplate(UUID templateId) {
        CertificateTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> ApiException.notFound("Certificate template", templateId));
        if (template.isDefaultTemplate()) {
            throw ApiException.conflict("default_template",
                    "Make another template the default before deleting this one.");
        }
        templateRepository.delete(template);
    }

    private TemplateView toTemplateView(CertificateTemplate template) {
        return new TemplateView(template.getId(), template.getName(), template.getHtmlTemplate(),
                template.getOrientation(), template.getValidityMonths(), template.isDefaultTemplate(),
                template.getUpdatedAt());
    }
}
