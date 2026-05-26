package com.dynamis.sep_api.backoffice.web.controller;

import com.dynamis.sep_api.backoffice.application.usecase.AssumirItemFilaUseCase;
import com.dynamis.sep_api.backoffice.application.usecase.ConsultarItemFilaUseCase;
import com.dynamis.sep_api.backoffice.application.usecase.ListarFilaOperacionalUseCase;
import com.dynamis.sep_api.backoffice.application.usecase.MarcarItemIgnoradoUseCase;
import com.dynamis.sep_api.backoffice.application.usecase.MarcarItemResolvidoUseCase;
import com.dynamis.sep_api.backoffice.application.usecase.RegistrarComentarioUseCase;
import com.dynamis.sep_api.backoffice.application.dto.ItemFilaDetalhe;
import com.dynamis.sep_api.backoffice.domain.exception.ItemFilaNaoEncontradoException;
import com.dynamis.sep_api.backoffice.domain.exception.JustificativaInvalidaException;
import com.dynamis.sep_api.backoffice.domain.exception.TransicaoItemInvalidaException;
import com.dynamis.sep_api.backoffice.domain.model.ComentarioInterno;
import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BackofficeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    ApiExceptionHandler.class,
    BackofficeControllerTest.MethodSecurityTestConfig.class,
    StepUpEnforcementAspect.class
})
class BackofficeControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    @org.springframework.context.annotation.EnableAspectJAutoProxy
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ListarFilaOperacionalUseCase listarUseCase;
    @MockBean private ConsultarItemFilaUseCase consultarUseCase;
    @MockBean private AssumirItemFilaUseCase assumirUseCase;
    @MockBean private RegistrarComentarioUseCase registrarComentarioUseCase;
    @MockBean private MarcarItemResolvidoUseCase resolverUseCase;
    @MockBean private MarcarItemIgnoradoUseCase ignorarUseCase;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private StepUpTokenService stepUpTokenService;
    @MockBean private UsuarioRepository usuarioRepository;

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(UUID id, Role role, boolean mfaHabilitado) {
        UsuarioAutenticado p = new UsuarioAutenticado(id, "op@sep.test", role);
        var auth = new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        Usuario u = Usuario.criar("op@sep.test", "hash", role);
        if (mfaHabilitado) u.marcarMfaHabilitado();
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
    }

    private ItemFilaOperacional novoItem() {
        return ItemFilaOperacional.abrir(
                TipoItemFila.ONBOARDING_ERRO,
                PrioridadeItem.ALTA,
                TipoEntidadeReferenciada.ONBOARDING,
                UUID.randomUUID(),
                "Onboarding REPROVADO",
                null,
                OffsetDateTime.now());
    }

    @Test
    void listar_backoffice200() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, false);
        when(listarUseCase.listar(any(), any())).thenReturn(new PageImpl<>(List.of(
                com.dynamis.sep_api.backoffice.application.dto.ItemFilaSummary.de(novoItem()))));

        mockMvc.perform(get("/api/v1/backoffice/fila")).andExpect(status().isOk());
    }

    @Test
    void consultar_naoEncontrado_404() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, false);
        UUID id = UUID.randomUUID();
        when(consultarUseCase.consultar(id)).thenThrow(new ItemFilaNaoEncontradoException(id));

        mockMvc.perform(get("/api/v1/backoffice/fila/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void consultar_happy200() throws Exception {
        UUID id = UUID.randomUUID();
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, false);
        ItemFilaOperacional item = novoItem();
        when(consultarUseCase.consultar(any())).thenReturn(ItemFilaDetalhe.de(item, List.of(), null));

        mockMvc.perform(get("/api/v1/backoffice/fila/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void assumir_happy200() throws Exception {
        UUID id = UUID.randomUUID();
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, false);
        ItemFilaOperacional item = novoItem();
        item.assumir(UUID.randomUUID());
        when(assumirUseCase.executar(any(), any())).thenReturn(item);

        mockMvc.perform(post("/api/v1/backoffice/fila/{id}/assumir", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_TRATAMENTO"));
    }

    @Test
    void assumir_transicaoInvalida_409() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, false);
        when(assumirUseCase.executar(any(), any()))
                .thenThrow(new TransicaoItemInvalidaException(StatusItemFila.RESOLVIDO, StatusItemFila.EM_TRATAMENTO));

        mockMvc.perform(post("/api/v1/backoffice/fila/{id}/assumir", UUID.randomUUID()))
                .andExpect(status().isConflict());
    }

    @Test
    void comentar_happy201() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, false);
        ComentarioInterno c = ComentarioInterno.registrar(UUID.randomUUID(), UUID.randomUUID(), "obs operacional");
        when(registrarComentarioUseCase.executar(any(), any(), any())).thenReturn(c);

        mockMvc.perform(post("/api/v1/backoffice/fila/{id}/comentarios", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conteudo\":\"obs operacional\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void comentar_conteudoVazio_400() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, false);

        mockMvc.perform(post("/api/v1/backoffice/fila/{id}/comentarios", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conteudo\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(registrarComentarioUseCase, never()).executar(any(), any(), any());
    }

    @Test
    void resolver_happy200() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, false);
        ItemFilaOperacional item = novoItem();
        item.assumir(UUID.randomUUID());
        item.resolver(OffsetDateTime.now());
        when(resolverUseCase.executar(any(), any(), any())).thenReturn(item);

        mockMvc.perform(patch("/api/v1/backoffice/fila/{id}/resolver", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificativa\":\"Documento recebido e validado manualmente\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVIDO"));
    }

    @Test
    void resolver_justificativaCurta_400() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, false);

        mockMvc.perform(patch("/api/v1/backoffice/fila/{id}/resolver", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificativa\":\"curta\"}"))
                .andExpect(status().isBadRequest());
        verify(resolverUseCase, never()).executar(any(), any(), any());
    }

    @Test
    void resolver_useCaseLancaJustificativaInvalida_400() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, false);
        when(resolverUseCase.executar(any(), any(), any())).thenThrow(new JustificativaInvalidaException());

        mockMvc.perform(patch("/api/v1/backoffice/fila/{id}/resolver", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificativa\":\"Justificativa valida com mais de vinte chars\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resolver_semStepUpComMfaHabilitado_403() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, true);

        mockMvc.perform(patch("/api/v1/backoffice/fila/{id}/resolver", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificativa\":\"Justificativa valida com mais de vinte chars\"}"))
                .andExpect(status().isForbidden());
        verify(resolverUseCase, never()).executar(any(), any(), any());
    }

    @Test
    void ignorar_happy200() throws Exception {
        autenticar(UUID.randomUUID(), Role.BACKOFFICE, false);
        ItemFilaOperacional item = novoItem();
        item.ignorar(OffsetDateTime.now());
        when(ignorarUseCase.executar(any(), any(), any())).thenReturn(item);

        mockMvc.perform(patch("/api/v1/backoffice/fila/{id}/ignorar", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificativa\":\"Item duplicado de outro fluxo ja em tratamento\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IGNORADO"));
    }
}
