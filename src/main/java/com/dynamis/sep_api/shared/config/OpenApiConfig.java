package com.dynamis.sep_api.shared.config;

import com.dynamis.sep_api.identity.infrastructure.security.RequireStepUp;
import com.dynamis.sep_api.identity.infrastructure.security.RequireStepUpEstrito;
import com.dynamis.sep_api.identity.infrastructure.security.StepUpEnforcementAspect;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Configuracao OpenAPI/Swagger UI da SEP API. PRD §11/§13/§22 — documentacao via Springdoc com
 * security scheme HTTP Bearer JWT global. Endpoints publicos (cadastro, login, webhooks)
 * permanecem liberados no {@code SecurityConfig}; o requirement global serve apenas para habilitar
 * o botao "Authorize" na Swagger UI.
 */
@Configuration
public class OpenApiConfig implements WebMvcConfigurer {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    /**
     * Publica enums como schema nomeado em {@code components/schemas}, com {@code $ref} nos usos, em
     * vez de repetir a lista de valores inline em cada campo (Sprint 35 Task 35.7).
     *
     * <p>{@code enumsAsRef} e campo <b>estatico</b> do {@code ModelResolver} do swagger-core, e nao
     * uma property do springdoc — por isso a configuracao e um bean e nao uma linha de
     * {@code application.yml}. O {@code ObjectMapper} vem do contexto para o resolver enxergar as
     * mesmas regras de serializacao que o runtime aplica.
     */
    @Bean
    public ModelResolver modelResolver(ObjectMapper objectMapper) {
        ModelResolver.enumsAsRef = true;
        return new ModelResolver(objectMapper);
    }

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
     * Declara o {@code 400} que o servico <b>ja devolve</b> quando um parametro do path nao parseia
     * (Sprint 35 Task 35.8). Nao inventa comportamento: o {@code ApiExceptionHandler} mapeia
     * {@code MethodArgumentTypeMismatchException} para {@code BAD_REQUEST} desde antes desta sprint —
     * o que faltava era o contrato publicar o status, e sem isso o consumidor nao tem como ramificar.
     * Desbloqueia a <b>F-24.5</b> no {@code sep-app}.
     *
     * <p><b>Por que um customizer e nao 31 anotacoes.</b> O perimetro medido no Gate 35.0 e de 31
     * operacoes, em 19 controllers. Anotar uma a uma deixaria o contrato correto hoje e errado no
     * proximo endpoint que alguem escrever — o mesmo padrao de deriva que a Sprint 34 fechou com o
     * {@code stepUpTokenHeaderCustomizer}. Aqui a regra e derivada do <b>handler</b>, entao endpoint
     * novo com identificador tipado no path nasce declarado.
     *
     * <p>O criterio e "path variable de tipo <b>nao textual</b>", e nao "UUID": um
     * {@code @PathVariable} de enum falha exatamente do mesmo jeito — e o caso de
     * {@code tipoChamada} em {@code BackofficeReprocessoController}, o unico endpoint que ja
     * declarava o {@code 400} na mao. Path variable {@code String} nao tem como falhar no parse, e
     * por isso nao ganha o status.
     *
     * <p>Nao sobrescreve declaracao existente: onde o autor ja escreveu um {@code 400} com descricao
     * propria, ela prevalece.
     */
    @Bean
    public OperationCustomizer badRequestDePathVariableCustomizer() {
        return (operation, handlerMethod) -> {
            ApiResponses respostas = operation.getResponses();
            if (respostas == null || respostas.containsKey("400") || !temPathVariableTipada(handlerMethod)) {
                return operation;
            }
            respostas.addApiResponse(
                    "400",
                    new ApiResponse()
                            .description("Identificador do path em formato invalido"
                                    + " (por exemplo, um UUID malformado)."));
            return operation;
        };
    }

    /**
     * {@code String} no path nunca falha no parse; qualquer outro tipo pode. Espelha exatamente o que
     * o {@code ApiExceptionHandler} trata, e nao uma lista de tipos escrita a mao que envelheceria.
     */
    private static boolean temPathVariableTipada(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(parametro -> parametro.hasParameterAnnotation(PathVariable.class)
                        && !CharSequence.class.isAssignableFrom(parametro.getParameterType()));
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
