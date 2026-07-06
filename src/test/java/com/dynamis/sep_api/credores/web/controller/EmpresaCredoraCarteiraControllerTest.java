package com.dynamis.sep_api.credores.web.controller;

import com.dynamis.sep_api.credores.application.dto.PixOperacaoStatusView;
import com.dynamis.sep_api.credores.application.usecase.AssociarOperacaoFinanciadaUseCase;
import com.dynamis.sep_api.credores.application.usecase.ConsultarCarteiraCredoraUseCase;
import com.dynamis.sep_api.credores.application.usecase.ConsultarOperacaoCarteiraUseCase;
import com.dynamis.sep_api.credores.application.usecase.ConsultarStatusPixOperacaoCredoraUseCase;
import com.dynamis.sep_api.credores.domain.exception.StatusPixOperacaoNaoEncontradoException;
import com.dynamis.sep_api.credores.web.mapper.CarteiraCredoraWebMapper;
import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.identity.infrastructure.security.JwtAuthenticationFilter;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EmpresaCredoraCarteiraController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, EmpresaCredoraCarteiraControllerTest.MethodSecurityTestConfig.class})
class EmpresaCredoraCarteiraControllerTest {

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConsultarCarteiraCredoraUseCase consultarCarteiraUseCase;

    @MockBean
    private ConsultarOperacaoCarteiraUseCase consultarOperacaoUseCase;

    @MockBean
    private ConsultarStatusPixOperacaoCredoraUseCase consultarStatusPixOperacaoUseCase;

    @MockBean
    private AssociarOperacaoFinanciadaUseCase associarUseCase;

    @MockBean
    private CarteiraCredoraWebMapper mapper;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private StepUpTokenService stepUpTokenService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(UUID id) {
        UsuarioAutenticado p = new UsuarioAutenticado(id, "credora@sep.test", Role.CLIENTE);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        Usuario u = Usuario.criar("credora@sep.test", "hash", Role.CLIENTE);
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
    }

    @Test
    void credoraOwner200_semConsumirStepUp() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UUID operacaoId = UUID.randomUUID();
        autenticar(usuarioId);
        when(consultarStatusPixOperacaoUseCase.executar(eq(usuarioId), eq(operacaoId)))
                .thenReturn(new PixOperacaoStatusView("LIQUIDADO", new BigDecimal("1500.00"), OffsetDateTime.now()));

        mockMvc.perform(get("/api/v1/credores/carteira/{id}/pix", operacaoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIQUIDADO"))
                .andExpect(jsonPath("$.valor").value(1500.00));

        verifyNoInteractions(stepUpTokenService);
    }

    @Test
    void semCredoraOperacaoOuPix404() throws Exception {
        UUID operacaoId = UUID.randomUUID();
        autenticar(UUID.randomUUID());
        when(consultarStatusPixOperacaoUseCase.executar(any(), any()))
                .thenThrow(new StatusPixOperacaoNaoEncontradoException());

        mockMvc.perform(get("/api/v1/credores/carteira/{id}/pix", operacaoId)).andExpect(status().isNotFound());
    }

    @Test
    void idInvalido400() throws Exception {
        autenticar(UUID.randomUUID());
        mockMvc.perform(get("/api/v1/credores/carteira/{id}/pix", "nao-eh-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void jsonNaoContemDadosProibidos() throws Exception {
        autenticar(UUID.randomUUID());
        when(consultarStatusPixOperacaoUseCase.executar(any(), any()))
                .thenReturn(
                        new PixOperacaoStatusView("EM_PROCESSAMENTO", new BigDecimal("2000.00"), OffsetDateTime.now()));

        mockMvc.perform(get("/api/v1/credores/carteira/{id}/pix", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tomadorId").doesNotExist())
                .andExpect(jsonPath("$.contratoId").doesNotExist())
                .andExpect(jsonPath("$.chaveDestinoMascara").doesNotExist())
                .andExpect(jsonPath("$.txid").doesNotExist())
                .andExpect(jsonPath("$.transferenciaId").doesNotExist())
                .andExpect(jsonPath("$.justificativa").doesNotExist());
    }
}
