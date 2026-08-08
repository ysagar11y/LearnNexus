package com.learnnexus.notification;

import com.learnnexus.config.AppProperties;
import com.learnnexus.iam.User;
import com.learnnexus.config.DesignSystem;
import com.learnnexus.tenancy.TenantContext;
import com.learnnexus.tenant.TenantBranding;
import com.learnnexus.tenant.TenantBrandingRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Renders and delivers transactional email using each tenant's own branding.
 *
 * <p>Sends are asynchronous and never propagate failures: a bounced notification
 * must not roll back the enrolment or certificate that triggered it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final TenantBrandingRepository brandingRepository;
    private final AppProperties properties;

    /** Copy for one branded email. */
    public record Message(
            String to,
            String subject,
            String heading,
            String body,
            String ctaLabel,
            String ctaUrl,
            String footnote
    ) {}

    @Async
    public void send(Message message, TenantContext.Snapshot tenant) {
        if (!properties.mail().enabled()) {
            log.debug("Mail disabled; would have sent '{}' to {}", message.subject(), message.to());
            return;
        }
        try {
            TenantBranding branding = brandingRepository.findById(tenant.tenantId()).orElse(null);

            Context context = new Context();
            context.setVariable("heading", message.heading());
            context.setVariable("body", message.body());
            context.setVariable("ctaLabel", message.ctaLabel());
            context.setVariable("ctaUrl", message.ctaUrl());
            context.setVariable("footnote", message.footnote());
            context.setVariable("tenantName", tenant.name());
            context.setVariable("logoUrl", branding == null ? null : branding.getLogoUrl());
            context.setVariable("brandHue", branding == null ? DesignSystem.DEFAULT_BRAND_HUE : branding.getBrandHue());
            context.setVariable("supportEmail", branding == null ? null : branding.getSupportEmail());
            context.setVariable("emailFooter", branding == null ? null : branding.getEmailFooter());

            String html = templateEngine.process("email/notification", context);

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(html, true);

            String fromName = branding != null && branding.getEmailFromName() != null
                    ? branding.getEmailFromName()
                    : tenant.name() + " via " + properties.mail().fromName();
            helper.setFrom(properties.mail().fromAddress(), fromName);

            mailSender.send(mime);
            log.debug("Sent '{}' to {}", message.subject(), message.to());
        } catch (UnsupportedEncodingException | jakarta.mail.MessagingException | org.springframework.mail.MailException ex) {
            log.error("Failed to send '{}' to {}: {}", message.subject(), message.to(), ex.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // Concrete transactional emails
    // -----------------------------------------------------------------

    public void sendPasswordReset(User user, TenantContext.Snapshot tenant, String token) {
        String url = portalUrl(tenant, "/reset-password?token=" + encode(token));
        send(new Message(
                user.getEmail(),
                "Reset your " + tenant.name() + " password",
                "Reset your password",
                "Hi " + user.getFirstName() + ", we received a request to reset the password for your "
                        + tenant.name() + " account. This link is valid for two hours.",
                "Choose a new password",
                url,
                "If you did not request this, you can safely ignore this email — your password will not change."
        ), tenant);
    }

    public void sendInvitation(User user, TenantContext.Snapshot tenant, String token, String inviterName) {
        String url = portalUrl(tenant, "/accept-invite?token=" + encode(token));
        send(new Message(
                user.getEmail(),
                inviterName + " invited you to " + tenant.name(),
                "You have been invited",
                inviterName + " has invited you to join " + tenant.name()
                        + " on LearnNexus. Set a password to activate your account and see the courses assigned to you.",
                "Accept invitation",
                url,
                "This invitation expires in seven days."
        ), tenant);
    }

    public void sendCourseAssigned(User user, TenantContext.Snapshot tenant,
                                   String courseTitle, String courseId, String dueDescription) {
        send(new Message(
                user.getEmail(),
                "New course assigned: " + courseTitle,
                courseTitle,
                "Hi " + user.getFirstName() + ", " + courseTitle + " has been added to your learning plan."
                        + (dueDescription == null ? "" : " " + dueDescription),
                "Start learning",
                portalUrl(tenant, "/learn/" + courseId),
                null
        ), tenant);
    }

    public void sendDeadlineReminder(User user, TenantContext.Snapshot tenant,
                                     String courseTitle, String courseId, String dueDescription) {
        send(new Message(
                user.getEmail(),
                "Reminder: " + courseTitle + " is due soon",
                courseTitle + " is due soon",
                "Hi " + user.getFirstName() + ", " + dueDescription
                        + " You can pick up where you left off at any time.",
                "Continue course",
                portalUrl(tenant, "/learn/" + courseId),
                null
        ), tenant);
    }

    public void sendCertificateIssued(User user, TenantContext.Snapshot tenant,
                                      String courseTitle, String verificationCode) {
        send(new Message(
                user.getEmail(),
                "Your certificate for " + courseTitle,
                "Congratulations",
                "Hi " + user.getFirstName() + ", you have completed " + courseTitle
                        + ". Your certificate is ready to download and share.",
                "View certificate",
                portalUrl(tenant, "/certificates"),
                "Anyone can confirm this certificate is genuine at "
                        + properties.publicBaseUrl() + "/verify/" + verificationCode
        ), tenant);
    }

    private String portalUrl(TenantContext.Snapshot tenant, String path) {
        // Single-host development builds address the tenant with a query parameter;
        // deployed environments give each tenant its own sub-domain.
        String base = properties.publicBaseUrl();
        String separator = path.contains("?") ? "&" : "?";
        return base + path + separator + "tenant=" + tenant.slug();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public Optional<TenantBranding> brandingFor(TenantContext.Snapshot tenant) {
        return brandingRepository.findById(tenant.tenantId());
    }
}
