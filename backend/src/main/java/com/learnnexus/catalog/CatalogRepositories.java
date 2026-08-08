package com.learnnexus.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositories for the catalog aggregate, grouped so the module stays legible. */
public final class CatalogRepositories {

    private CatalogRepositories() {
    }

    public interface CategoryRepository extends JpaRepository<Category, UUID> {

        @Query("select c from Category c order by c.sortOrder, c.name")
        List<Category> findAllOrdered();

        boolean existsBySlugIgnoreCase(String slug);
    }

    public interface CourseRepository extends JpaRepository<Course, UUID> {

        @Query("select c from Course c where c.id = :id and c.deletedAt is null")
        Optional<Course> findActiveById(@Param("id") UUID id);

        @Query("select c from Course c where lower(c.slug) = lower(:slug) and c.deletedAt is null")
        Optional<Course> findBySlug(@Param("slug") String slug);

        boolean existsBySlugIgnoreCase(String slug);

        @Query("""
                select c from Course c
                where c.deletedAt is null
                  and (cast(:query as string) is null
                       or lower(c.title)   like lower(concat('%', cast(:query as string), '%'))
                       or lower(c.summary) like lower(concat('%', cast(:query as string), '%')))
                  and (:status is null or c.status = :status)
                  and (:categoryId is null or c.categoryId = :categoryId)
                  and (:level is null or c.level = :level)
                  and (:deliveryType is null or c.deliveryType = :deliveryType)
                """)
        Page<Course> search(@Param("query") String query,
                            @Param("status") Course.Status status,
                            @Param("categoryId") UUID categoryId,
                            @Param("level") Course.Level level,
                            @Param("deliveryType") Course.DeliveryType deliveryType,
                            Pageable pageable);

        @Query("select c from Course c where c.deletedAt is null and c.id in :ids")
        List<Course> findAllActiveByIds(@Param("ids") Collection<UUID> ids);

        @Query("select count(c) from Course c where c.deletedAt is null and c.status = :status")
        long countByStatus(@Param("status") Course.Status status);

        @Query("select c from Course c where c.deletedAt is null and c.ownerId = :ownerId order by c.updatedAt desc")
        List<Course> findByOwner(@Param("ownerId") UUID ownerId);
    }

    public interface CourseModuleRepository extends JpaRepository<CourseModule, UUID> {

        @Query("select m from CourseModule m where m.courseId = :courseId order by m.sortOrder, m.createdAt")
        List<CourseModule> findByCourse(@Param("courseId") UUID courseId);

        @Query("select coalesce(max(m.sortOrder), -1) from CourseModule m where m.courseId = :courseId")
        short maxSortOrder(@Param("courseId") UUID courseId);

        @Modifying
        @Query("delete from CourseModule m where m.courseId = :courseId")
        void deleteByCourse(@Param("courseId") UUID courseId);
    }

    public interface LessonRepository extends JpaRepository<Lesson, UUID> {

        @Query("select l from Lesson l where l.courseId = :courseId order by l.sortOrder, l.createdAt")
        List<Lesson> findByCourse(@Param("courseId") UUID courseId);

        @Query("select l from Lesson l where l.moduleId = :moduleId order by l.sortOrder, l.createdAt")
        List<Lesson> findByModule(@Param("moduleId") UUID moduleId);

        @Query("select count(l) from Lesson l where l.courseId = :courseId")
        long countByCourse(@Param("courseId") UUID courseId);

        @Query("select count(l) from Lesson l where l.courseId = :courseId and l.mandatory = true")
        long countMandatoryByCourse(@Param("courseId") UUID courseId);

        @Query("select coalesce(sum(l.durationSeconds), 0) from Lesson l where l.courseId = :courseId")
        long totalDurationSeconds(@Param("courseId") UUID courseId);

        @Query("select coalesce(max(l.sortOrder), -1) from Lesson l where l.moduleId = :moduleId")
        short maxSortOrder(@Param("moduleId") UUID moduleId);
    }

    public interface CourseInstructorRepository extends JpaRepository<CourseInstructor, CourseInstructor.Key> {

        @Query("select ci from CourseInstructor ci where ci.id.courseId = :courseId")
        List<CourseInstructor> findByCourse(@Param("courseId") UUID courseId);

        @Query("select ci.id.courseId from CourseInstructor ci where ci.id.userId = :userId")
        List<UUID> findCourseIdsForInstructor(@Param("userId") UUID userId);

        @Query("select count(ci) from CourseInstructor ci where ci.id.courseId = :courseId and ci.id.userId = :userId")
        long countAssignment(@Param("courseId") UUID courseId, @Param("userId") UUID userId);

        @Modifying
        @Query("delete from CourseInstructor ci where ci.id.courseId = :courseId")
        void deleteByCourse(@Param("courseId") UUID courseId);
    }
}
