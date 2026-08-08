package com.learnnexus.catalog;

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

@Entity
@Table(name = "lessons")
@Getter
@Setter
@NoArgsConstructor
public class Lesson extends TenantScoped {

    public enum ContentType {
        VIDEO, PDF, HTML, AUDIO, SCORM, LINK, QUIZ;

        /**
         * Time-based formats can be completed by watching; the rest are marked
         * complete explicitly by the learner.
         */
        public boolean isTimeBased() {
            return this == VIDEO || this == AUDIO;
        }
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType = ContentType.HTML;

    @Column(name = "content_url")
    private String contentUrl;

    @Column(name = "content_html", columnDefinition = "text")
    private String contentHtml;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds = 0;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder = 0;

    @Column(name = "is_preview", nullable = false)
    private boolean preview = false;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
