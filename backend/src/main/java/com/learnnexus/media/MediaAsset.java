package com.learnnexus.media;

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
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "media_assets")
@Getter
@Setter
@NoArgsConstructor
public class MediaAsset extends TenantScoped {

    public enum Kind {
        VIDEO, PDF, IMAGE, AUDIO, SCORM, OTHER;

        public static Kind fromContentType(String contentType, String filename) {
            String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
            if (type.startsWith("video/")) return VIDEO;
            if (type.startsWith("audio/")) return AUDIO;
            if (type.startsWith("image/")) return IMAGE;
            if (type.equals("application/pdf")) return PDF;
            if (type.equals("application/zip") && filename != null
                    && filename.toLowerCase(Locale.ROOT).contains("scorm")) return SCORM;
            return OTHER;
        }
    }

    /**
     * PENDING until the browser's direct-to-storage upload completes and the
     * client confirms it; only READY assets may be attached to a lesson.
     */
    public enum Status { PENDING, READY, FAILED }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind = Kind.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
