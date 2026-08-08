package com.learnnexus.enrollment;

import java.util.Optional;
import java.util.UUID;

/**
 * Issued from the enrolment module when a course is completed. Declared as an
 * interface here so enrolment does not depend on certificate internals — and so
 * the completion path can be tested without PDF rendering.
 */
public interface CertificateIssuer {

    /**
     * Issues a certificate for a completed enrolment, or returns the existing one.
     * Returns empty when the course has certificates switched off.
     */
    Optional<UUID> issueFor(UUID enrollmentId);

    Optional<UUID> findForEnrollment(UUID enrollmentId);
}
