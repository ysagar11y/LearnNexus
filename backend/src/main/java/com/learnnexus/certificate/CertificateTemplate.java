package com.learnnexus.certificate;

import com.learnnexus.tenancy.TenantScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A tenant-editable certificate design.
 *
 * <p>Stored as XHTML with Thymeleaf placeholders and rendered to PDF at issue
 * time. Keeping the design as markup rather than a fixed layout means a customer
 * can match their own house style without a code change.
 */
@Entity
@Table(name = "certificate_templates")
@Getter
@Setter
@NoArgsConstructor
public class CertificateTemplate extends TenantScoped {

    public enum Orientation { LANDSCAPE, PORTRAIT }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String name;

    @Column(name = "html_template", nullable = false, columnDefinition = "text")
    private String htmlTemplate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Orientation orientation = Orientation.LANDSCAPE;

    /** Null means the certificate never expires. */
    @Column(name = "validity_months")
    private Integer validityMonths;

    @Column(name = "is_default", nullable = false)
    private boolean defaultTemplate = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
