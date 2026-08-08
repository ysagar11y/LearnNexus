package com.learnnexus.certificate;

import com.learnnexus.common.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a certificate's stored XHTML into a PDF.
 *
 * <p>Uses its own Thymeleaf engine with a string resolver, because the tenant's
 * template lives in the database rather than on the classpath. Flying Saucer then
 * lays the XHTML out with CSS paged-media rules.
 */
@Slf4j
@Component
public class CertificateRenderer {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final TemplateEngine engine;

    public CertificateRenderer() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.XML);
        // Certificates are rendered on demand and templates change rarely; caching
        // the compiled form would only serve a stale design after an edit.
        resolver.setCacheable(false);

        this.engine = new TemplateEngine();
        this.engine.setTemplateResolver(resolver);
    }

    /** Values a template may reference. */
    public record Fields(
            String recipientName,
            String courseTitle,
            String tenantName,
            String issuedOn,
            String expiresOn,
            String score,
            String serialNumber,
            String verificationCode,
            String verificationUrl,
            String logoUrl,
            int brandHue
    ) {}

    public static Fields fieldsFor(Certificate certificate, String tenantName, String logoUrl,
                                   int brandHue, String verificationUrl) {
        return new Fields(
                certificate.getRecipientName(),
                certificate.getCourseTitle(),
                tenantName,
                DATE_FORMAT.format(certificate.getIssuedAt()),
                certificate.getExpiresAt() == null ? null : DATE_FORMAT.format(certificate.getExpiresAt()),
                certificate.getScore() == null ? null : certificate.getScore().stripTrailingZeros().toPlainString() + "%",
                certificate.getSerialNumber(),
                certificate.getVerificationCode(),
                verificationUrl,
                logoUrl,
                brandHue);
    }

    public byte[] render(String htmlTemplate, Fields fields, CertificateTemplate.Orientation orientation) {
        Context context = new Context();
        context.setVariables(Map.of(
                "recipientName", nullSafe(fields.recipientName()),
                "courseTitle", nullSafe(fields.courseTitle()),
                "tenantName", nullSafe(fields.tenantName()),
                "issuedOn", nullSafe(fields.issuedOn()),
                "serialNumber", nullSafe(fields.serialNumber()),
                "verificationCode", nullSafe(fields.verificationCode()),
                "verificationUrl", nullSafe(fields.verificationUrl()),
                "brandHue", fields.brandHue()));
        // setVariables above is capped at ten entries by Map.of; the optional
        // fields are added separately so a null does not blow up the map factory.
        context.setVariable("expiresOn", fields.expiresOn());
        context.setVariable("score", fields.score());
        context.setVariable("logoUrl", fields.logoUrl());

        String xhtml = engine.process(withPageRule(htmlTemplate, orientation), context);

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(xhtml);
            renderer.layout();
            renderer.createPDF(output);
            return output.toByteArray();
        } catch (Exception ex) {
            log.error("Certificate rendering failed", ex);
            throw ApiException.unprocessable("certificate_render_failed",
                    "The certificate template could not be rendered. Check that it is valid XHTML.");
        }
    }

    /**
     * Injects the page size so a template author does not have to remember the
     * paged-media incantation for landscape output.
     */
    private String withPageRule(String template, CertificateTemplate.Orientation orientation) {
        String rule = orientation == CertificateTemplate.Orientation.LANDSCAPE
                ? "@page { size: 297mm 210mm; margin: 0; }"
                : "@page { size: 210mm 297mm; margin: 0; }";
        String styleTag = "<style type=\"text/css\">" + rule + "</style>";

        int headEnd = template.indexOf("</head>");
        if (headEnd > -1) {
            return template.substring(0, headEnd) + styleTag + template.substring(headEnd);
        }
        return styleTag + template;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    public static String defaultTemplate() {
        return DEFAULT_TEMPLATE;
    }

    /**
     * The template a new tenant starts with. Written as strict XHTML with inline
     * styles because Flying Saucer parses it as XML and supports a subset of CSS 2.1.
     */
    private static final String DEFAULT_TEMPLATE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:th="http://www.thymeleaf.org">
            <head>
              <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
              <title>Certificate</title>
              <style type="text/css">
                body { margin: 0; padding: 0; font-family: Helvetica, Arial, sans-serif; color: #1b1a17; }
                .sheet { width: 297mm; height: 210mm; position: relative; background: #fbfaf7; }
                .rule { position: absolute; left: 14mm; top: 14mm; right: 14mm; bottom: 14mm;
                        border: 1.2pt solid #c9c3b4; }
                .inner { position: absolute; left: 18mm; top: 18mm; right: 18mm; bottom: 18mm;
                         border: 0.4pt solid #ded8c9; }
                .content { position: absolute; left: 30mm; top: 30mm; right: 30mm; text-align: center; }
                .eyebrow { font-size: 9pt; letter-spacing: 3.4pt; text-transform: uppercase;
                           color: #8a8474; margin: 0 0 10mm 0; }
                .name { font-family: Georgia, 'Times New Roman', serif; font-size: 34pt; font-weight: normal;
                        margin: 0 0 5mm 0; color: #14130f; }
                .underline { width: 90mm; border-bottom: 0.8pt solid #c9c3b4; margin: 0 auto 9mm auto; }
                .lead { font-size: 11pt; color: #55524a; margin: 0 0 4mm 0; }
                .course { font-family: Georgia, 'Times New Roman', serif; font-size: 19pt;
                          margin: 0 0 9mm 0; color: #14130f; }
                .meta { font-size: 9.5pt; color: #6d6a60; margin: 0; }
                .footer { position: absolute; left: 30mm; right: 30mm; bottom: 26mm;
                          font-size: 8pt; color: #8a8474; }
                .footer-left { float: left; text-align: left; }
                .footer-right { float: right; text-align: right; }
                .issuer { font-size: 10pt; color: #55524a; letter-spacing: 0.6pt; }
              </style>
            </head>
            <body>
              <div class="sheet">
                <div class="rule"></div>
                <div class="inner"></div>

                <div class="content">
                  <p class="eyebrow">Certificate of Completion</p>
                  <p class="lead">This is to certify that</p>
                  <h1 class="name" th:text="${recipientName}">Recipient</h1>
                  <div class="underline"></div>
                  <p class="lead">has successfully completed</p>
                  <p class="course" th:text="${courseTitle}">Course title</p>
                  <p class="meta">
                    <span th:text="'Issued ' + ${issuedOn}">Issued</span>
                    <span th:if="${score}" th:text="' · Final score ' + ${score}"></span>
                    <span th:if="${expiresOn}" th:text="' · Valid until ' + ${expiresOn}"></span>
                  </p>
                  <p class="issuer" style="margin-top:12mm;" th:text="${tenantName}">Organisation</p>
                </div>

                <div class="footer">
                  <div class="footer-left">
                    <span th:text="'Certificate no. ' + ${serialNumber}">Serial</span>
                  </div>
                  <div class="footer-right">
                    <span th:text="'Verify at ' + ${verificationUrl}">Verify</span>
                  </div>
                </div>
              </div>
            </body>
            </html>
            """;
}
