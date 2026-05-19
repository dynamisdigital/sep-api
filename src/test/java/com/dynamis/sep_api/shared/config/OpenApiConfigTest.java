package com.dynamis.sep_api.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
class OpenApiConfigTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Test
    void apiDocsExpoeSchemasESecurity() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                        .value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat")
                        .value("JWT"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/me']").exists())
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
                        .exists());
    }
}
