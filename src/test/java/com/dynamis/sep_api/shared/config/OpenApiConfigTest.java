package com.dynamis.sep_api.shared.config;

import com.dynamis.sep_api.contratos.web.controller.ContratoController;
import com.dynamis.sep_api.identity.infrastructure.security.RequireStepUpEstrito;
import com.dynamis.sep_api.identity.infrastructure.security.StepUpEnforcementAspect;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
class OpenApiConfigTest {

    private static final String API_DOCS = "/v3/api-docs";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void apiDocsExpoeSchemasESecurity() throws Exception {
        mockMvc()
                .perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                        .value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat")
                        .value("JWT"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me']").exists())
                // Sprint 34 Task 34.5: a F-22.6 consome esta rota pelo snapshot exportado; fora do
                // contrato ela nao chega ao contract:check do sep-app.
                .andExpect(jsonPath("$.paths['/api/v1/auth/politica-lockout']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/usuarios']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/usuarios/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/usuarios/{id}/senha']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/onboarding/pessoa']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/onboarding/pessoa/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/onboarding/pessoa/{id}/documentos']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/onboarding/pessoa/{id}/verificar']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/onboarding/empresa']").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/onboarding/empresa/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/onboarding/empresa/{id}/documentos']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/onboarding/empresa/{id}/verificar']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/onboarding/empresa/{id}/representantes']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/webhooks/celcoin/kyc']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/webhooks/celcoin/kyb']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/webhooks/celcoin/pld']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/usuarios/{id}/role']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/credito/propostas']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/credito/propostas/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/credito/propostas/{id}/parecer']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/credito/propostas/{id}/regras']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/credito/propostas/{id}/open-finance/consentimento']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/credito/propostas/{id}/open-finance']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/webhooks/celcoin/open-finance']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/contratos/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contratos/proposta/{propostaId}']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/contratos/{id}/versoes']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/contratos/{id}/aceite']").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/contratos/{id}/cancelar']").exists())
                .andExpect(jsonPath("$.components.schemas.UsuarioCreateDto").exists())
                .andExpect(jsonPath("$.components.schemas.UsuarioResponseDto").exists())
                .andExpect(jsonPath("$.components.schemas.LoginRequestDto").exists())
                .andExpect(jsonPath("$.components.schemas.TokenResponseDto").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponseDto").exists())
                .andExpect(jsonPath("$.components.schemas.IniciarOnboardingRequest")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.OnboardingResponse").exists())
                .andExpect(jsonPath("$.components.schemas.StatusOnboardingResponse")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.IniciarOnboardingEmpresaRequest")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.EmpresaResponse").exists())
                .andExpect(jsonPath("$.components.schemas.StatusOnboardingEmpresaResponse")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.RepresentanteLegalResponse")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.ConsultaPldResumoResponse")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.CriarPropostaRequest").exists())
                .andExpect(jsonPath("$.components.schemas.PropostaResponse").exists())
                .andExpect(
                        jsonPath("$.components.schemas.RegistrarParecerRequest").exists())
                .andExpect(
                        jsonPath("$.components.schemas.ParecerCreditoResponse").exists())
                .andExpect(
                        jsonPath("$.components.schemas.RegraAvaliadaResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ScoreInternoResponse").exists())
                .andExpect(jsonPath("$.components.schemas.UsuarioRoleUpdateDto").exists())
                .andExpect(jsonPath("$.components.schemas.IniciarConsentimentoOpenFinanceRequest")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.IniciarConsentimentoOpenFinanceResponse")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.OpenFinanceStatusResponse")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.MovimentacaoConsolidadaResponse")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/pix/desembolsos']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/pix/desembolsos/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/pix/desembolsos/{id}/status']")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.SolicitarDesembolsoRequest")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.DesembolsoResponse").exists())
                .andExpect(jsonPath("$.components.schemas.StatusDesembolsoResponse")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/pix/recebimentos/referencias']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/pix/recebimentos/referencias/{id}']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/pix/recebimentos/{id}']").exists())
                .andExpect(jsonPath("$.components.schemas.GerarReferenciaRecebimentoRequest")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.ReferenciaRecebimentoResponse")
                        .exists())
                .andExpect(
                        jsonPath("$.components.schemas.RecebimentoPixResponse").exists());
    }

    // ===== Sprint 34 Task 34.7: regressao por propriedade =====
    // Ate aqui o teste so fazia .exists() sobre paths e schemas, entao nenhuma das declaracoes da
    // Task 34.6 era protegida: apagar as quatro de uma vez deixava a suite verde.

    /**
     * O {@code required} nao e cosmetico: {@code RequireStepUpEstrito} nao admite bypass e sempre
     * exige o token, enquanto {@code RequireStepUp} libera usuario sem MFA. Um customizer que
     * marcasse os 24 como obrigatorio faria o contrato rejeitar um cliente legitimo.
     */
    @Test
    void stepUpTokenDeclaradoComRequiredPorAnotacao() throws Exception {
        mockMvc()
                .perform(get(API_DOCS))
                .andExpect(jsonPath("$.paths['/api/v1/contratos/{id}/aceite'].patch.parameters" + "[?(@.name=='"
                                + StepUpEnforcementAspect.HEADER + "')].required")
                        .value(hasItem(true)))
                .andExpect(jsonPath("$.paths['/api/v1/usuarios/{id}/senha'].patch.parameters" + "[?(@.name=='"
                                + StepUpEnforcementAspect.HEADER + "')].required")
                        .value(hasItem(false)));
    }

    /**
     * Guarda de cobertura, nao de conteudo: a contagem casa endpoints anotados com endpoints
     * documentados. Uma terceira anotacao de step-up com {@code @Before} proprio — enforcement vivo,
     * header nao declarado — regenera exatamente a lacuna que a Task 34.6 fechou, e nenhum assert
     * fixo pegaria. Por isso o filtro e por prefixo do nome, e nao pelos dois tipos conhecidos.
     */
    @Test
    void todoEndpointAnotadoComStepUpDeclaraOHeader() throws Exception {
        // Pelo nome: o actuator registra um segundo RequestMappingHandlerMapping
        // (controllerEndpointHandlerMapping) e por tipo a busca fica ambigua.
        Map<RequestMappingInfo, HandlerMethod> handlers = context.getBean(
                        "requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
                .getHandlerMethods();
        Set<String> anotados = new TreeSet<>();
        Set<String> estritos = new TreeSet<>();
        handlers.forEach((info, hm) -> {
            boolean stepUp = Arrays.stream(hm.getMethod().getAnnotations())
                    .anyMatch(a -> a.annotationType().getSimpleName().startsWith("RequireStepUp"));
            if (stepUp) {
                anotados.addAll(operacoesDe(info));
                if (hm.getMethodAnnotation(RequireStepUpEstrito.class) != null) {
                    estritos.addAll(operacoesDe(info));
                }
            }
        });

        String json = mockMvc().perform(get(API_DOCS)).andReturn().getResponse().getContentAsString();
        Set<String> declarados = new TreeSet<>();
        Set<String> declaradosObrigatorios = new TreeSet<>();
        Map<String, Map<String, Object>> paths = JsonPath.read(json, "$.paths");
        paths.forEach((path, operacoes) -> operacoes.forEach((verbo, corpo) -> {
            if (!(corpo instanceof Map<?, ?> operacao)) {
                return;
            }
            for (Object parametro : parametrosDe(operacao)) {
                if (parametro instanceof Map<?, ?> p && StepUpEnforcementAspect.HEADER.equals(p.get("name"))) {
                    String chave = verbo.toUpperCase(Locale.ROOT) + " " + path;
                    declarados.add(chave);
                    if (Boolean.TRUE.equals(p.get("required"))) {
                        declaradosObrigatorios.add(chave);
                    }
                }
            }
        }));

        assertThat(anotados)
                .as("sem endpoints anotados o teste passaria provando nada")
                .isNotEmpty();
        // Conjuntos, e nao contagens: com hasSize, esquecer um endpoint e declarar o header duas
        // vezes em outro se cancelariam, e a falha diria "esperava 24, veio 24".
        assertThat(declarados)
                .as("todo endpoint anotado declara o header, e so eles")
                .containsExactlyInAnyOrderElementsOf(anotados);
        assertThat(declaradosObrigatorios)
                .as("obrigatorio exatamente nos estritos")
                .containsExactlyInAnyOrderElementsOf(estritos);
    }

    /** {@code "PATCH /api/v1/contratos/{id}/aceite"} para cada verbo mapeado. */
    private static Set<String> operacoesDe(RequestMappingInfo info) {
        Set<String> caminhos = info.getPathPatternsCondition() == null
                ? info.getPatternsCondition().getPatterns()
                : info.getPathPatternsCondition().getPatternValues();
        Set<String> chaves = new TreeSet<>();
        info.getMethodsCondition()
                .getMethods()
                .forEach(metodo -> caminhos.forEach(caminho -> chaves.add(metodo.name() + " " + caminho)));
        return chaves;
    }

    private static List<?> parametrosDe(Map<?, ?> operacao) {
        return operacao.get("parameters") instanceof List<?> lista ? lista : List.of();
    }

    /**
     * A lista esperada e <b>literal</b>, de proposito. Deriva-la de {@code values()} pareceria mais
     * limpo e nao provaria nada contra crescimento: os dois lados sairiam do mesmo enum e andariam
     * juntos — medido, acrescentar um valor ao {@code StatusFormalizacao} passava verde. Como o
     * schema e publico e {@code StatusFormalizacao} ja cresceu uma vez (Sprint 11) sem checkpoint na
     * fronteira web, publicar um valor novo tem que custar uma edicao deliberada aqui.
     *
     * <p>Trocar o campo de volta por {@code String} tambem fica vermelho: sem enum tipado a chave
     * {@code enum} some do schema e o jsonPath nao resolve.
     */
    @Test
    void enumsDeContratoEAssinaturaPublicadosNoSchema() throws Exception {
        Object[] statusFormalizacao = {
            "GERADO", "AGUARDANDO_ACEITE", "ACEITO", "EM_ASSINATURA", "ASSINADO", "RECUSADO", "CANCELADO"
        };

        mockMvc()
                .perform(get(API_DOCS))
                .andExpect(jsonPath("$.components.schemas.ContratoResponse.properties.tipo.enum")
                        .value(containsInAnyOrder("MUTUO", "CCB", "OUTROS")))
                .andExpect(jsonPath("$.components.schemas.ContratoResponse.properties.status.enum")
                        .value(containsInAnyOrder(statusFormalizacao)))
                .andExpect(jsonPath("$.components.schemas.StatusAssinaturaResponse.properties.statusContrato.enum")
                        .value(containsInAnyOrder(statusFormalizacao)))
                .andExpect(jsonPath("$.components.schemas.StatusAssinaturaResponse.properties.statusEnvelope.enum")
                        .value(containsInAnyOrder(
                                "RASCUNHO", "ENVIADO", "VISUALIZADO", "ASSINADO", "RECUSADO", "EXPIRADO")));
    }

    /**
     * Headers de resposta sao setados via {@code ResponseEntity.header(...)} e invisiveis ao
     * springdoc, entao so um assert explicito impede que voltem a sumir do contrato. O
     * {@code Retry-After} entra aqui pelo mesmo motivo — e a F-22.6 depende dele no snapshot.
     */
    @Test
    void headersDeRespostaDeclarados() throws Exception {
        mockMvc()
                .perform(get(API_DOCS))
                .andExpect(jsonPath("$.paths['/api/v1/contratos/{id}/documento-assinado'].get"
                                + ".responses.200.headers['" + ContratoController.HEADER_HASH_DOCUMENTO + "']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/contratos/{id}/documento-assinado'].get"
                                + ".responses.200.headers['Content-Disposition']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses.423"
                                + ".headers['Retry-After'].schema.type")
                        .value("integer"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses.429.headers['Retry-After']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/totp/verify'].post.responses.423"
                                + ".headers['Retry-After'].schema.type")
                        .value("integer"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/totp/verify'].post.responses.429.headers['Retry-After']")
                        .exists());
    }

    /**
     * O tipo declarado tem que ser o que o servidor emite. A Task 34.6 chegou a publicar
     * {@code number} aqui, seguindo uma premissa falsa sobre o Jackson; o {@code
     * JacksonAutoConfiguration} desliga {@code WRITE_DURATIONS_AS_TIMESTAMPS} e o fio leva ISO-8601.
     * A forma real esta fixada na {@code BackofficeIT}; este assert trava o outro lado do par.
     */
    @Test
    void durationDeclaradoComoStringComoOServidorEmite() throws Exception {
        mockMvc()
                .perform(get(API_DOCS))
                .andExpect(
                        jsonPath("$.components.schemas.DashboardResponse.properties" + ".tempoMedioResolucao30d.type")
                                .value("string"));
    }
}
