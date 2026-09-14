package com.dynamis.sep_api.notificacao.web.controller;

import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.notificacao.application.exception.NotificacaoNaoEncontradaException;
import com.dynamis.sep_api.notificacao.application.exception.PaginacaoInvalidaException;
import com.dynamis.sep_api.notificacao.application.usecase.ConsultarCentralNotificacoesUseCase;
import com.dynamis.sep_api.notificacao.application.usecase.MarcarNotificacaoLidaUseCase;
import com.dynamis.sep_api.notificacao.domain.model.Notificacao;
import com.dynamis.sep_api.notificacao.domain.vo.ConteudoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.OrigemNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.Referencia;
import com.dynamis.sep_api.notificacao.domain.vo.TipoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.TipoReferencia;
import com.dynamis.sep_api.shared.exception.ApiExceptionHandler;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fronteira HTTP da central com os casos de uso simulados: forma do JSON, minimizacao e codigos de
 * erro. O isolamento entre contas com banco e JWT reais esta no {@code CentralNotificacoesIT}.
 */
@WebMvcTest(controllers = NotificacaoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, NotificacaoControllerTest.MethodSecurityTestConfig.class})
class NotificacaoControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {}

    private static final OffsetDateTime CRIADA_EM = OffsetDateTime.of(2026, 9, 14, 10, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConsultarCentralNotificacoesUseCase consultar;

    @MockBean
    private MarcarNotificacaoLidaUseCase marcarLida;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private StepUpTokenService stepUpTokenService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    private UUID usuarioId;

    @BeforeEach
    void autenticar() {
        usuarioId = UUID.randomUUID();
        UsuarioAutenticado principal = new UsuarioAutenticado(usuarioId, "cliente@sep.test", Role.CLIENTE);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private Notificacao desembolso(UUID contratoId) {
        return Notificacao.disponibilizarInApp(
                usuarioId,
                new OrigemNotificacao(TipoNotificacao.DESEMBOLSO_PIX_CONCLUIDO, "transferencia-interna-1"),
                new ConteudoNotificacao(
                        "Desembolso concluido", "Mensagem", new Referencia(TipoReferencia.CONTRATO, contratoId)),
                CRIADA_EM);
    }

    @Test
    void listar_expoeSoOsCamposDaCentral() throws Exception {
        UUID contratoId = UUID.randomUUID();
        Notificacao notificacao = desembolso(contratoId);
        when(consultar.listar(usuarioId, 0, 20))
                .thenReturn(new PageImpl<>(List.of(notificacao), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/notificacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(notificacao.getId().toString()))
                .andExpect(jsonPath("$.content[0].tipo").value("DESEMBOLSO_PIX_CONCLUIDO"))
                .andExpect(jsonPath("$.content[0].titulo").value("Desembolso concluido"))
                .andExpect(jsonPath("$.content[0].mensagem").value("Mensagem"))
                .andExpect(jsonPath("$.content[0].criadaEm").exists())
                .andExpect(jsonPath("$.content[0].lidaEm").value(nullValue()))
                .andExpect(jsonPath("$.content[0].referencia.tipo").value("CONTRATO"))
                .andExpect(jsonPath("$.content[0].referencia.id").value(contratoId.toString()))
                .andExpect(jsonPath("$.content[0].usuarioId").doesNotExist())
                .andExpect(jsonPath("$.content[0].origem").doesNotExist())
                .andExpect(jsonPath("$.content[0].canal").doesNotExist())
                .andExpect(jsonPath("$.content[0].entrega").doesNotExist());
    }

    @Test
    void listar_repassaPaginaETamanhoInformados() throws Exception {
        when(consultar.listar(eq(usuarioId), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/notificacoes").param("page", "3").param("size", "50"))
                .andExpect(status().isOk());

        verify(consultar).listar(usuarioId, 3, 50);
    }

    @Test
    void listar_paginacaoInvalida_400ComCodigo() throws Exception {
        when(consultar.listar(eq(usuarioId), anyInt(), anyInt())).thenThrow(new PaginacaoInvalidaException());

        mockMvc.perform(get("/api/v1/notificacoes").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NTF-400-001"));
    }

    @Test
    void contagem_devolveONumeroDoUsuario() throws Exception {
        when(consultar.contarNaoLidas(usuarioId)).thenReturn(4L);

        mockMvc.perform(get("/api/v1/notificacoes/nao-lidas/contagem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.naoLidas").value(4));
    }

    @Test
    void marcarLida_devolveOItemComLeitura() throws Exception {
        Notificacao notificacao = desembolso(UUID.randomUUID());
        notificacao.marcarLida(CRIADA_EM.plusMinutes(5));
        when(marcarLida.marcar(usuarioId, notificacao.getId())).thenReturn(notificacao);

        mockMvc.perform(post("/api/v1/notificacoes/{id}/leitura", notificacao.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificacao.getId().toString()))
                .andExpect(jsonPath("$.lidaEm").exists());
    }

    @Test
    void marcarLida_naoEncontrada_404ComCodigoESemIdentificador() throws Exception {
        UUID id = UUID.randomUUID();
        when(marcarLida.marcar(any(), any())).thenThrow(new NotificacaoNaoEncontradaException());

        mockMvc.perform(post("/api/v1/notificacoes/{id}/leitura", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NTF-404-001"))
                .andExpect(jsonPath("$.message").value("Notificacao nao encontrada"));
    }

    @Test
    void marcarLida_idQueNaoEUuid_400SemChamarOCasoDeUso() throws Exception {
        mockMvc.perform(post("/api/v1/notificacoes/{id}/leitura", "nao-eh-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(marcarLida);
    }

    @Test
    void semAutenticacao_naoChegaAoCasoDeUso() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/notificacoes/nao-lidas/contagem"));

        verifyNoInteractions(consultar);
    }
}
