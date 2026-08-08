package com.learnnexus;

import com.learnnexus.tenancy.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.function.Supplier;

/**
 * Base class for tests that need a real database.
 *
 * <p>PostgreSQL specifically, not an in-memory substitute: the behaviour under
 * test depends on Hibernate's tenant discriminator, partial unique indexes,
 * array columns and an append-only trigger, none of which H2 reproduces
 * faithfully. A test that passed against H2 would prove nothing about the
 * isolation guarantee.
 *
 * <p>The database is obtained one of two ways:
 * <ul>
 *   <li>{@code TEST_DB_URL} set — use that database. This is how the suite runs
 *       when Maven itself is containerised (see {@code ./mvnw.sh}), because
 *       Testcontainers cannot reliably reach the Docker socket from inside a
 *       container on Docker Desktop.</li>
 *   <li>otherwise — start a throwaway PostgreSQL with Testcontainers. This is
 *       the path CI takes, and the one to prefer when running natively.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    private static final String EXTERNAL_URL = System.getenv("TEST_DB_URL");

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (EXTERNAL_URL == null) {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("learnnexus_test")
                    .withUsername("test")
                    .withPassword("test");
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (EXTERNAL_URL != null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> envOr("TEST_DB_USER", "learnnexus"));
            registry.add("spring.datasource.password", () -> envOr("TEST_DB_PASSWORD", "learnnexus"));
            // A reused database must be reset between runs, or yesterday's rows
            // silently change today's counts.
            registry.add("spring.flyway.clean-disabled", () -> "false");
            registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "none");
        } else {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }

        // The suite builds its own fixtures; demo data would only skew assertions.
        registry.add("app.seed.enabled", () -> "false");
        registry.add("app.mail.enabled", () -> "false");
    }

    private static String envOr(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @BeforeEach
    void clearTenantContext() {
        // Tests run on the JUnit thread, which the resolution filter never touches,
        // so a context left behind by a previous test would leak between them.
        TenantContext.clear();
    }

    /**
     * Runs a block in a transaction opened <em>after</em> the tenant is set —
     * the only ordering in which Hibernate's discriminator picks it up.
     */
    protected <T> T asTenant(TenantContext.Snapshot tenant, Supplier<T> action) {
        Object[] result = new Object[1];
        TenantContext.runAs(tenant, () -> result[0] = transactionTemplate.execute(status -> action.get()));
        @SuppressWarnings("unchecked")
        T typed = (T) result[0];
        return typed;
    }
}
