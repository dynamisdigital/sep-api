package com.dynamis.sep_api.credores.web.controller;

import com.dynamis.sep_api.credores.application.dto.AcaoDecisaoMatching;
import com.dynamis.sep_api.credores.application.dto.DecidirMatchingCredoraOperacaoCommand;
import com.dynamis.sep_api.credores.application.dto.GerarSugestoesMatchingResult;
import com.dynamis.sep_api.credores.application.dto.MatchingCredoraOperacaoView;
import com.dynamis.sep_api.credores.application.usecase.ConsultarMatchingCredoraOperacaoUseCase;
import com.dynamis.sep_api.credores.application.usecase.DecidirMatchingCredoraOperacaoUseCase;
import com.dynamis.sep_api.credores.application.usecase.GerarSugestoesMatchingCredoraUseCase;
import com.dynamis.sep_api.credores.application.usecase.ListarSugestoesMatchingCredoraUseCase;
import com.dynamis.sep_api.credores.domain.exception.MatchingDecisaoConflitanteException;
import com.dynamis.sep_api.credores.domain.exception.MatchingNaoEncontradoException;
import com.dynamis.sep_api.credores.domain.vo.StatusMatchingCredoraOperacao;
import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.StepUpEnforcementAspect;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Borda web do matching assistido (Sprint 30 Task 30.5): roles FINANCEIRO/ADMIN nos GETs sem
 * step-up, step-up estrito somente no POST /decisao, 400 para acao invalida, 404 neutro, 409 em
 * terminal e DTO minimo sem campos internos (motivo, decisor, snapshot bruto).
 */
@WebMvcTest(controllers = MatchingCredoraController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    ApiExceptionHandler.class,
    MatchingCredoraControllerTest.MethodSecurityTestConfig.class,
    StepUpEnforcementAspect.class
})
class MatchingCredoraControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    @EnableAspectJAutoProxy
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GerarSugestoesMatchingCredoraUseCase gerarUseCase;

    @MockBean
    private ListarSugestoesMatchingCredoraUseCase listarUseCase;

    @MockBean
    private ConsultarMatchingCredoraOperacaoUseCase consultarUseCase;

    @MockBean
    private DecidirMatchingCredoraOperacaoUseCase decidirUseCase;

    @MockBean
    private StepUpTokenService stepUpTokenService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private final UUID operadorId = UUID.randomUUID();
    private final UUID sugestaoId = UUID.randomUUID();

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(Role role) {
        UsuarioAutenticado principal = new UsuarioAutenticado(operadorId, "op@sep.test", role);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void mfaHabilitado(boolean habilitado) {
        Usuario usuario = org.mockito.Mockito.mock(Usuario.class);
        when(usuario.isMfaHabilitado()).thenReturn(habilitado);
        when(usuarioRepository.findById(operadorId)).thenReturn(Optional.of(usuario));
    }

    private void stepUpValido() {
        when(stepUpTokenService.validarEConsumir("tok-ok")).thenReturn(Optional.of(operadorId));
    }

    private MatchingCredoraOperacaoView view(StatusMatchingCredoraOperacao status) {
        return new MatchingCredoraOperacaoView(
                sugestaoId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                status,
                new BigDecimal("10000.00"),
                List.of("CREDORA_ATIVA", "CONTRATO_ASSINADO"),
                OffsetDateTime.now(),
                status == StatusMatchingCredoraOperacao.SUGERIDA ? null : OffsetDateTime.now());
    }

    @Test
    void financeiroListaSugestoes_200_comRefreshSemStepUp() throws Exception {
        autenticar(Role.FINANCEIRO);
        when(gerarUseCase.executar(operadorId)).thenReturn(new GerarSugestoesMatchingResult(1));
        when(listarUseCase.executar()).thenReturn(List.of(view(StatusMatchingCredoraOperacao.SUGERIDA)));

        mockMvc.perform(get("/api/v1/credores/matching/sugestoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(sugestaoId.toString()))
                .andExpect(jsonPath("$[0].status").value("SUGERIDA"))
                .andExpect(jsonPath("$[0].valorElegivel").value(10000.00))
                .andExpect(jsonPath("$[0].criterios[0]").value("CREDORA_ATIVA"))
                .andExpect(jsonPath("$[0].motivoDecisaoSanitizado").doesNotExist())
                .andExpect(jsonPath("$[0].decididoPorUsuarioId").doesNotExist())
                .andExpect(jsonPath("$[0].criteriosSnapshot").doesNotExist());

        verify(gerarUseCase).executar(operadorId);
        verifyNoInteractions(stepUpTokenService);
    }

    @Test
    void adminListaSugestoes_200() throws Exception {
        autenticar(Role.ADMIN);
        when(gerarUseCase.executar(operadorId)).thenReturn(new GerarSugestoesMatchingResult(0));
        when(listarUseCase.executar()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/credores/matching/sugestoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void clienteNaoListaSugestoes_403() throws Exception {
        autenticar(Role.CLIENTE);

        mockMvc.perform(get("/api/v1/credores/matching/sugestoes")).andExpect(status().isForbidden());

        verifyNoInteractions(gerarUseCase, listarUseCase);
    }

    @Test
    void financeiroConsultaSugestao_200_semStepUp() throws Exception {
        autenticar(Role.FINANCEIRO);
        when(consultarUseCase.executar(sugestaoId)).thenReturn(view(StatusMatchingCredoraOperacao.CONFIRMADA));

        mockMvc.perform(get("/api/v1/credores/matching/{sugestaoId}", sugestaoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sugestaoId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMADA"))
                .andExpect(jsonPath("$.decididaEm").exists());

        verifyNoInteractions(stepUpTokenService);
    }

    @Test
    void clienteNaoConsultaSugestao_403() throws Exception {
        autenticar(Role.CLIENTE);

        mockMvc.perform(get("/api/v1/credores/matching/{sugestaoId}", sugestaoId))
                .andExpect(status().isForbidden());

        verifyNoInteractions(consultarUseCase);
    }

    @Test
    void consultaInexistente_404NeutroSemUuid() throws Exception {
        autenticar(Role.ADMIN);
        when(consultarUseCase.executar(sugestaoId)).thenThrow(new MatchingNaoEncontradoException());

        mockMvc.perform(get("/api/v1/credores/matching/{sugestaoId}", sugestaoId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(
                        "$.message",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(sugestaoId.toString()))));
    }

    @Test
    void financeiroDecideComStepUp_200() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();
        when(decidirUseCase.executar(any())).thenReturn(view(StatusMatchingCredoraOperacao.CONFIRMADA));

        mockMvc.perform(post("/api/v1/credores/matching/{sugestaoId}/decisao", sugestaoId)
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"CONFIRMAR\",\"motivo\":\"aderente\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADA"))
                .andExpect(jsonPath("$.motivoDecisaoSanitizado").doesNotExist());

        ArgumentCaptor<DecidirMatchingCredoraOperacaoCommand> captor =
                ArgumentCaptor.forClass(DecidirMatchingCredoraOperacaoCommand.class);
        verify(decidirUseCase).executar(captor.capture());
        assertThat(captor.getValue().sugestaoId()).isEqualTo(sugestaoId);
        assertThat(captor.getValue().acao()).isEqualTo(AcaoDecisaoMatching.CONFIRMAR);
        assertThat(captor.getValue().motivo()).isEqualTo("aderente");
        assertThat(captor.getValue().atorId()).isEqualTo(operadorId);
    }

    @Test
    void adminRejeitaComStepUp_200() throws Exception {
        autenticar(Role.ADMIN);
        mfaHabilitado(true);
        stepUpValido();
        when(decidirUseCase.executar(any())).thenReturn(view(StatusMatchingCredoraOperacao.REJEITADA));

        mockMvc.perform(post("/api/v1/credores/matching/{sugestaoId}/decisao", sugestaoId)
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"REJEITAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJEITADA"));
    }

    @Test
    void decisaoSemStepUpToken_403() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);

        mockMvc.perform(post("/api/v1/credores/matching/{sugestaoId}/decisao", sugestaoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"CONFIRMAR\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(decidirUseCase);
    }

    @Test
    void decisaoSemMfa_403_estritoSemBypass() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(false);
        stepUpValido();

        mockMvc.perform(post("/api/v1/credores/matching/{sugestaoId}/decisao", sugestaoId)
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"CONFIRMAR\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(decidirUseCase);
    }

    @Test
    void clienteNaoDecide_403() throws Exception {
        autenticar(Role.CLIENTE);

        mockMvc.perform(post("/api/v1/credores/matching/{sugestaoId}/decisao", sugestaoId)
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"CONFIRMAR\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(decidirUseCase);
    }

    @Test
    void acaoInvalida_400() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();

        mockMvc.perform(post("/api/v1/credores/matching/{sugestaoId}/decisao", sugestaoId)
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"PENSAR\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(decidirUseCase);
    }

    @Test
    void acaoAusente_400() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();

        mockMvc.perform(post("/api/v1/credores/matching/{sugestaoId}/decisao", sugestaoId)
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"sem acao\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(decidirUseCase);
    }

    @Test
    void decisaoInexistente_404NeutroSemUuid() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();
        when(decidirUseCase.executar(any())).thenThrow(new MatchingNaoEncontradoException());

        mockMvc.perform(post("/api/v1/credores/matching/{sugestaoId}/decisao", sugestaoId)
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"CONFIRMAR\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(
                        "$.message",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(sugestaoId.toString()))));
    }

    @Test
    void decisaoEmTerminal_409() throws Exception {
        autenticar(Role.FINANCEIRO);
        mfaHabilitado(true);
        stepUpValido();
        when(decidirUseCase.executar(any())).thenThrow(new MatchingDecisaoConflitanteException());

        mockMvc.perform(post("/api/v1/credores/matching/{sugestaoId}/decisao", sugestaoId)
                        .header("X-Step-Up-Token", "tok-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"REJEITAR\"}"))
                .andExpect(status().isConflict());
    }
}
