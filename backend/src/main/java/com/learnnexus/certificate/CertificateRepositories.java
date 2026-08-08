package com.learnnexus.certificate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CertificateRepositories {

    private CertificateRepositories() {
    }

    public interface CertificateTemplateRepository extends JpaRepository<CertificateTemplate, UUID> {

        @Query("select t from CertificateTemplate t order by t.defaultTemplate desc, t.name")
        List<CertificateTemplate> findAllOrdered();

        @Query("select t from CertificateTemplate t where t.defaultTemplate = true")
        Optional<CertificateTemplate> findDefault();
    }

    public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

        @Query("select c from Certificate c where c.userId = :userId order by c.issuedAt desc")
        List<Certificate> findForUser(@Param("userId") UUID userId);

        @Query("select c from Certificate c where c.enrollmentId = :enrollmentId and c.revokedAt is null")
        Optional<Certificate> findForEnrollment(@Param("enrollmentId") UUID enrollmentId);

        @Query("""
                select c from Certificate c
                where (:courseId is null or c.courseId = :courseId)
                  and (:userId is null or c.userId = :userId)
                order by c.issuedAt desc
                """)
        Page<Certificate> search(@Param("courseId") UUID courseId,
                                 @Param("userId") UUID userId,
                                 Pageable pageable);

        /**
         * Verification is deliberately unscoped by tenant: anyone holding a code —
         * a recruiter, an auditor — must be able to check it without knowing which
         * workspace issued it. The code is a 32-character random token, so it is
         * the credential, and only non-identifying fields are ever returned.
         */
        @Query(value = "select * from certificates where verification_code = :code", nativeQuery = true)
        Optional<Certificate> findByVerificationCodeAcrossTenants(@Param("code") String code);

        @Query(value = "select count(*) from certificates where serial_number = :serial", nativeQuery = true)
        long countBySerialAcrossTenants(@Param("serial") String serial);
    }
}
