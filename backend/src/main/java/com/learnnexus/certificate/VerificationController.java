package com.learnnexus.certificate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Certificate verification for people outside the platform — a recruiter or an
 * auditor holding a printed certificate. Intentionally unauthenticated and not
 * tenant-scoped: the verification code itself is the credential.
 */
@Tag(name = "Verification", description = "Public certificate verification.")
@RestController
@RequestMapping("/api/v1/verify")
@RequiredArgsConstructor
public class VerificationController {

    private final CertificateService certificateService;

    @Operation(summary = "Confirm a certificate is genuine using the code printed on it")
    @GetMapping("/{code}")
    public CertificateService.VerificationResult verify(@PathVariable String code) {
        return certificateService.verify(code);
    }
}
