package com.learnnexus.tenancy;

import com.learnnexus.AbstractIntegrationTest;
import com.learnnexus.catalog.CatalogRepositories;
import com.learnnexus.catalog.Course;
import com.learnnexus.common.Slugs;
import com.learnnexus.iam.RoleCode;
import com.learnnexus.iam.User;
import com.learnnexus.iam.UserRepository;
import com.learnnexus.tenant.Tenant;
import com.learnnexus.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The isolation guarantee, tested rather than asserted in a comment.
 *
 * <p>Every one of these is a real cross-tenant access attempt through the same
 * code path the application uses. If any of them starts passing data across a
 * tenant boundary, the product's central promise is broken.
 */
@DisplayName("Tenant isolation")
class TenantIsolationIT extends AbstractIntegrationTest {

    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired CatalogRepositories.CourseRepository courseRepository;

    private TenantContext.Snapshot alpha;
    private TenantContext.Snapshot beta;
    private UUID alphaCourseId;
    private UUID alphaUserId;

    @BeforeEach
    void seedTwoTenants() {
        alpha = createTenant("alpha-" + UUID.randomUUID().toString().substring(0, 8), "Alpha Ltd");
        beta = createTenant("beta-" + UUID.randomUUID().toString().substring(0, 8), "Beta GmbH");

        alphaCourseId = asTenant(alpha, () -> createCourse("Alpha Internals").getId());
        alphaUserId = asTenant(alpha, () -> createUser("someone@alpha.test").getId());

        asTenant(beta, () -> createCourse("Beta Handbook").getId());
        asTenant(beta, () -> createUser("someone@beta.test").getId());
    }

    @Test
    @DisplayName("a tenant's course list contains only its own courses")
    void courseListsAreScoped() {
        List<String> alphaTitles = asTenant(alpha, () ->
                courseRepository.findAll().stream().map(Course::getTitle).toList());
        List<String> betaTitles = asTenant(beta, () ->
                courseRepository.findAll().stream().map(Course::getTitle).toList());

        assertThat(alphaTitles).containsExactly("Alpha Internals");
        assertThat(betaTitles).containsExactly("Beta Handbook");
    }

    @Test
    @DisplayName("fetching another tenant's course by its exact id returns nothing")
    void findByIdCannotCrossTenants() {
        // The id is a valid, existing primary key — the only thing stopping the
        // read is the discriminator Hibernate appends.
        var fromBeta = asTenant(beta, () -> courseRepository.findActiveById(alphaCourseId));
        assertThat(fromBeta).isEmpty();

        var fromAlpha = asTenant(alpha, () -> courseRepository.findActiveById(alphaCourseId));
        assertThat(fromAlpha).isPresent();
    }

    @Test
    @DisplayName("fetching another tenant's user by id returns nothing")
    void userLookupCannotCrossTenants() {
        assertThat(asTenant(beta, () -> userRepository.findActiveById(alphaUserId))).isEmpty();
        assertThat(asTenant(alpha, () -> userRepository.findActiveById(alphaUserId))).isPresent();
    }

    @Test
    @DisplayName("searching by another tenant's email finds nothing")
    void emailSearchCannotCrossTenants() {
        assertThat(asTenant(beta, () -> userRepository.findByEmail("someone@alpha.test"))).isEmpty();
        assertThat(asTenant(alpha, () -> userRepository.findByEmail("someone@alpha.test"))).isPresent();
    }

    @Test
    @DisplayName("counts are per tenant, not global")
    void countsAreScoped() {
        assertThat(asTenant(alpha, () -> userRepository.countActive())).isEqualTo(1);
        assertThat(asTenant(beta, () -> userRepository.countActive())).isEqualTo(1);
    }

    @Test
    @DisplayName("the same email may exist in two tenants without colliding")
    void emailsAreUniquePerTenantNotGlobally() {
        asTenant(alpha, () -> createUser("shared@example.com").getId());
        // The unique index is (tenant_id, lower(email)), so this must not throw.
        UUID inBeta = asTenant(beta, () -> createUser("shared@example.com").getId());

        assertThat(inBeta).isNotNull();
        assertThat(asTenant(alpha, () -> userRepository.findByEmail("shared@example.com")))
                .isPresent();
        assertThat(asTenant(beta, () -> userRepository.findByEmail("shared@example.com")))
                .isPresent();
    }

    @Test
    @DisplayName("with no tenant resolved, tenant-scoped reads return nothing")
    void unresolvedTenantFailsClosed() {
        // The sentinel must never behave like a wildcard: a bug that loses the
        // tenant context has to produce empty results, never everyone's data.
        List<Course> courses = transactionTemplate.execute(status -> courseRepository.findAll());
        assertThat(courses).isEmpty();
    }

    @Test
    @DisplayName("an insert is stamped with the acting tenant, not a caller-supplied one")
    void insertsAreStampedWithTheActingTenant() {
        UUID id = asTenant(beta, () -> createCourse("Beta Second").getId());

        Course loaded = asTenant(beta, () -> courseRepository.findActiveById(id).orElseThrow());
        assertThat(loaded.getTenantId()).isEqualTo(beta.tenantId());
        assertThat(asTenant(alpha, () -> courseRepository.findActiveById(id))).isEmpty();
    }

    // -----------------------------------------------------------------

    private TenantContext.Snapshot createTenant(String slug, String name) {
        return transactionTemplate.execute(status -> {
            Tenant tenant = new Tenant(UUID.randomUUID(), slug, name);
            tenant.setStatus(Tenant.Status.ACTIVE);
            tenantRepository.save(tenant);
            return new TenantContext.Snapshot(tenant.getId(), slug, name, false);
        });
    }

    private Course createCourse(String title) {
        Course course = new Course();
        course.setTitle(title);
        course.setSlug(Slugs.of(title) + "-" + UUID.randomUUID().toString().substring(0, 6));
        course.setStatus(Course.Status.PUBLISHED);
        return courseRepository.save(course);
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Test");
        user.setStatus(User.Status.ACTIVE);
        user.setPasswordHash("$2a$12$notarealhashnotarealhashnotarealhashnotarealhashnotar");
        user.setRoleSet(Set.of(RoleCode.LEARNER));
        return userRepository.save(user);
    }
}
