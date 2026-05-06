package com.dynamis.sep_api.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao OpenAPI/Swagger UI da SEP API. PRD §11/§13/§22 — documentacao via Springdoc com
 * security scheme HTTP Bearer JWT global. Endpoints publicos (cadastro, login, webhooks)
 * permanecem liberados no {@code SecurityConfig}; o requirement global serve apenas para habilitar
 * o botao "Authorize" na Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI sepOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("SEP API").version("0.0.1").description("API REST da plataforma SEP"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(
                                SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
