package com.learnnexus.catalog;

import com.learnnexus.common.ApiException;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
public class Course extends TenantScoped {

    public enum Level { BEGINNER, INTERMEDIATE, ADVANCED }

    public enum DeliveryType { SELF_PACED, ILT_VIRTUAL, ILT_CLASSROOM, BLENDED, LEARNING_PATH }

    public enum Status { DRAFT, IN_REVIEW, PUBLISHED, ARCHIVED }

    public enum EnrollmentMode { MANUAL, SELF, INVITE }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    private String code;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String slug;

    private String summary;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Level level = Level.BEGINNER;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false)
    private DeliveryType deliveryType = DeliveryType.SELF_PACED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(nullable = false)
    private String language = "en";

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes = 0;

    @Column(name = "seat_limit")
    private Integer seatLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_mode", nullable = false)
    private EnrollmentMode enrollmentMode = EnrollmentMode.MANUAL;

    @Column(name = "passing_score", nullable = false)
    private short passingScore = 70;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", nullable = false)
    private String[] tags = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "prerequisite_ids", nullable = false)
    private UUID[] prerequisiteIds = new UUID[0];

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory = false;

    @Column(name = "certificate_enabled", nullable = false)
    private boolean certificateEnabled = true;

    @Column(name = "certificate_template_id")
    private UUID certificateTemplateId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isPublished() {
        return status == Status.PUBLISHED && deletedAt == null;
    }

    public boolean allowsSelfEnrollment() {
        return enrollmentMode == EnrollmentMode.SELF && isPublished();
    }

    /**
     * Guards the publish transition. A course with no lessons would let learners
     * "complete" it instantly and mint a meaningless certificate.
     */
    public void publish(long lessonCount) {
        if (status == Status.PUBLISHED) {
            return;
        }
        if (lessonCount == 0) {
            throw ApiException.badRequest("course_empty",
                    "Add at least one lesson before publishing this course.");
        }
        this.status = Status.PUBLISHED;
        this.publishedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
