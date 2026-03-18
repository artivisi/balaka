package com.artivisi.accountingfinance.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Configuration
@Slf4j
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_BEARER = "bearer";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Balaka API")
                        .version("2026.03")
                        .description("REST API for Indonesian accounting application. "
                                + "Supports AI-assisted transaction entry, financial analysis, "
                                + "bank reconciliation, tax export, and data import.")
                        .license(new License()
                                .name("Apache License, Version 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .schemaRequirement(SECURITY_SCHEME_BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme(SECURITY_SCHEME_BEARER)
                        .bearerFormat("opaque")
                        .description("OAuth 2.0 Device Flow token. "
                                + "Obtain via POST /api/device/code + POST /api/device/token. "
                                + "Token expires in 30 days."))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_BEARER));
    }

    @Bean
    public GroupedOpenApi apiGroup() {
        return GroupedOpenApi.builder()
                .group("api")
                .packagesToScan("com.artivisi.accountingfinance.controller.api")
                .build();
    }

    @Bean
    public OpenApiCustomizer extensionsCustomizer() {
        return openApi -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                ClassPathResource resource = new ClassPathResource("openapi/extensions.json");
                try (InputStream is = resource.getInputStream()) {
                    Map<String, Object> extensions = mapper.readValue(is,
                            new TypeReference<>() {});
                    extensions.forEach(openApi::addExtension);
                    log.info("Loaded {} OpenAPI x- extensions", extensions.size());
                }
            } catch (IOException e) {
                log.error("Failed to load openapi/extensions.json", e);
                throw new IllegalStateException("Cannot load openapi/extensions.json", e);
            }
        };
    }
}
