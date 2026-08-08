package com.learnnexus.catalog;

import com.learnnexus.tenancy.TenantScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Co-teaching assignment. Separate from {@code Course.ownerId} so a course can be
 * owned by an author while several instructors deliver it.
 */
@Entity
@Table(name = "course_instructors")
@Getter
@Setter
@NoArgsConstructor
public class CourseInstructor extends TenantScoped {

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "course_id", nullable = false)
        private UUID courseId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        public Key(UUID courseId, UUID userId) {
            this.courseId = courseId;
            this.userId = userId;
        }
    }

    @EmbeddedId
    private Key id = new Key();

    public CourseInstructor(UUID courseId, UUID userId) {
        this.id = new Key(courseId, userId);
    }

    public UUID getCourseId() {
        return id.getCourseId();
    }

    public UUID getUserId() {
        return id.getUserId();
    }
}
