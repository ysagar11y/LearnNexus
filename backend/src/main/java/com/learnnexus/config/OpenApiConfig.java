package com.learnnexus.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI learnNexusOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LearnNexus API")
                        .version("1.0.0")
                        .description("""
                                Multi-tenant learning management platform.

                                **Tenant resolution.** Every request is scoped to exactly one tenant,
                                resolved in this order: the `X-Tenant` header, an exact custom-domain
                                match on the host, then a sub-domain of the configured root domain.
                                A token issued for one tenant is rejected when presented against
                                another.

                                **Authentication.** `POST /api/v1/auth/login` returns a short-lived
                                access token and a rotating refresh token. Send the access token as
                                `Authorization: Bearer <token>`.
                                """)
                        .contact(new Contact().name("LearnNexus").email("engineering@learnnexus.app"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Local development"),
                        new Server().url("https://api.learnnexus.app").description("Production")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from /api/v1/auth/login"))
                        .addSecuritySchemes("tenantHeader", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Tenant")
                                .description("Tenant slug; only needed when the host does not identify the tenant")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("bearerAuth")
                        .addList("tenantHeader"));
    }
}
