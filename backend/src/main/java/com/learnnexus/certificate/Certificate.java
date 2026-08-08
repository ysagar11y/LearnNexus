package com.learnnexus.certificate;

import com.learnnexus.tenancy.TenantScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An issued certificate.
 *
 * <p>The recipient name and course title are copied onto the row rather than
 * joined at read time: a certificate is evidence of what was true on the day it
 * was issued, and must not silently change when a course is renamed or a learner
 * is removed.
 */
@Entity
@Table(name = "certificates")
@Getter
@Setter
@NoArgsConstructor
public class Certificate extends TenantScoped {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "enrollment_id")
    private UUID enrollmentId;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "serial_number", nullable = false)
    private String serialNumber;

    /** Public, unguessable token used by the verification page. */
    @Column(name = "verification_code", nullable = false)
    private String verificationCode;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "course_title", nullable = false)
    private String courseTitle;

    private BigDecimal score;

    @Column(name = "pdf_key")
    private String pdfKey;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    public boolean isValid() {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }

    public boolean isExpired() {
        return revokedAt == null && expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
