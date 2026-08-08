package com.learnnexus.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Repository scanning.
 *
 * <p>Several modules group their repositories as nested interfaces inside a
 * single holder ({@code CatalogRepositories}, {@code EnrollmentRepositories}, …)
 * so a module's persistence surface reads as one file. Spring Data skips nested
 * interfaces unless {@code considerNestedRepositories} is switched on, which is
 * the only reason this class exists — Boot's auto-configuration would otherwise
 * cover everything here.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.learnnexus",
        considerNestedRepositories = true)
public class PersistenceConfig {
}
