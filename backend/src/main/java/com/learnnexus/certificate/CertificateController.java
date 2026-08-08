package com.learnnexus.certificate;

import com.learnnexus.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Certificates", description = "Issued credentials, templates and verification.")
@RestController
@RequestMapping("/api/v1/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private static final String CAN_ADMINISTER = "hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')";

    private final CertificateService certificateService;

    public record RevokeRequest(String reason) {}

    @Operation(summary = "The signed-in learner's certificate wallet")
    @GetMapping("/mine")
    public List<CertificateService.CertificateView> mine() {
        return certificateService.myCertificates();
    }

    @Operation(summary = "Search issued certificates")
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN','INSTRUCTOR','MANAGER')")
    public PageResponse<CertificateService.CertificateView> search(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return certificateService.search(courseId, userId, page, size);
    }

    @Operation(summary = "A single certificate")
    @GetMapping("/{certificateId}")
    public CertificateService.CertificateView get(@PathVariable UUID certificateId) {
        return certificateService.get(certificateId);
    }

    @Operation(summary = "Download the certificate as a PDF")
    @GetMapping(value = "/{certificateId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> download(@PathVariable UUID certificateId) {
        var certificate = certificateService.get(certificateId);
        byte[] pdf = certificateService.download(certificateId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(certificate.serialNumber() + ".pdf").build().toString())
                .body(pdf);
    }

    @Operation(summary = "Revoke a certificate")
    @PostMapping("/{certificateId}/revoke")
    @PreAuthorize(CAN_ADMINISTER)
    public CertificateService.CertificateView revoke(@PathVariable UUID certificateId,
                                                     @RequestBody(required = false) RevokeRequest request) {
        return certificateService.revoke(certificateId, request == null ? null : request.reason());
    }

    // ---------------- Templates ----------------

    @Operation(summary = "Certificate templates for this workspace")
    @GetMapping("/templates")
    @PreAuthorize(CAN_ADMINISTER)
    public List<CertificateService.TemplateView> templates() {
        return certificateService.templates();
    }

    @Operation(summary = "Create a template")
    @PostMapping("/templates")
    @PreAuthorize(CAN_ADMINISTER)
    public CertificateService.TemplateView create(@RequestBody CertificateService.TemplateRequest request) {
        return certificateService.saveTemplate(null, request);
    }

    @Operation(summary = "Update a template")
    @PutMapping("/templates/{templateId}")
    @PreAuthorize(CAN_ADMINISTER)
    public CertificateService.TemplateView update(@PathVariable UUID templateId,
                                                  @RequestBody CertificateService.TemplateRequest request) {
        return certificateService.saveTemplate(templateId, request);
    }

    @Operation(summary = "Render a template with sample data to preview the design")
    @PostMapping(value = "/templates/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(CAN_ADMINISTER)
    public ResponseEntity<byte[]> preview(@RequestBody CertificateService.TemplateRequest request) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"certificate-preview.pdf\"")
                .body(certificateService.previewTemplate(request));
    }

    @Operation(summary = "Delete a non-default template")
    @DeleteMapping("/templates/{templateId}")
    @PreAuthorize(CAN_ADMINISTER)
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID templateId) {
        certificateService.deleteTemplate(templateId);
        return ResponseEntity.noContent().build();
    }
}
