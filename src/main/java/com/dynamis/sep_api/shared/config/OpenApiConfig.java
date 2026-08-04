package com.dynamis.sep_api.shared.config;

import com.dynamis.sep_api.identity.infrastructure.security.RequireStepUp;
import com.dynamis.sep_api.identity.infrastructure.security.RequireStepUpEstrito;
import com.dynamis.sep_api.identity.infrastructure.security.StepUpEnforcementAspect;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuracao OpenAPI/Swagger UI da SEP API. PRD §11/§13/§22 — documentacao via Springdoc com
 * security scheme HTTP Bearer JWT global. Endpoints publicos (cadastro, login, webhooks)
 * permanecem liberados no {@code SecurityConfig}; o requirement global serve apenas para habilitar
 * o botao "Authorize" na Swagger UI.
 */
@Configuration
public class OpenApiConfig implements WebMvcConfigurer {

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

    /**
     * Declara {@code X-Step-Up-Token} nos 24 endpoints anotados (Sprint 34 Task 34.6).
     *
     * <p>O aspect le o header por {@code RequestContextHolder}, e nao como {@code @RequestHeader} na
     * assinatura, entao o springdoc nao tem como inferi-lo — a lacuna que o {@code contract:check} do
     * {@code sep-app} carregava desde a F-Sprint 19, com {@code appliesTo: "*"} silenciando 18
     * endpoints de uma vez. Um customizer lendo a propria anotacao fecha os 24 e <b>nao dessincroniza
     * do aspect</b>: o nome vem de {@link StepUpEnforcementAspect#HEADER}, a mesma constante que a
     * validacao usa. Declarar {@code @Parameter} nos 24 pontos ja nasceria sujeita a drift — e o
     * proprio motivo de a lacuna existir.
     *
     * <p>{@code required} distingue as duas anotacoes, que <b>nao</b> sao equivalentes:
     * {@link RequireStepUpEstrito} nao tem bypass e sempre exige o token, enquanto
     * {@link RequireStepUp} libera a chamada quando o usuario nao tem MFA habilitado (migracao
     * pre-MFA da Sprint 5) — para esses, o header e condicional, e marca-lo obrigatorio faria o
     * contrato mentir contra um cliente legitimo.
     *
     * <p>Funciona mesmo onde a anotacao esta escrita por nome totalmente qualificado inline
     * ({@code CobrancaController} e partes de {@code UsuarioController}/{@code MfaController}):
     * {@code getMethodAnnotation} resolve o tipo, nao o texto.
     */
    @Bean
    public OperationCustomizer stepUpTokenHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            boolean estrito = handlerMethod.getMethodAnnotation(RequireStepUpEstrito.class) != null;
            boolean comBypass = handlerMethod.getMethodAnnotation(RequireStepUp.class) != null;
            if (!estrito && !comBypass) {
                return operation;
            }
            operation.addParametersItem(new HeaderParameter()
                    .name(StepUpEnforcementAspect.HEADER)
                    .required(estrito)
                    .description(
                            estrito
                                    ? "Token de step-up de uso unico. Obrigatorio: esta operacao nao admite bypass."
                                    : "Token de step-up de uso unico. Dispensavel apenas para usuario sem MFA"
                                            + " habilitado (bypass de migracao pre-MFA).")
                    .schema(new StringSchema()));
            return operation;
        };
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/swagger-ui", "/swagger-ui/index.html");
    }
}
