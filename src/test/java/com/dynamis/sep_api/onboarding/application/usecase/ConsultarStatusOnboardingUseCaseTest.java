package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.application.dto.StatusOnboardingView;
import com.dynamis.sep_api.onboarding.domain.exception.OnboardingNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.model.DocumentoCadastral;
import com.dynamis.sep_api.onboarding.domain.model.ResultadoVerificacao;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.DocumentoCadastralRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ResultadoVerificacaoRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsultarStatusOnboardingUseCaseTest {

    private SolicitacaoOnboardingRepository solicitacaoRepository;
    private DocumentoCadastralRepository documentoRepository;
    private ResultadoVerificacaoRepository resultadoRepository;
    private ConsultarStatusOnboardingUseCase useCase;

    private UUID usuarioId;
    private SolicitacaoOnboarding solicitacao;

    @BeforeEach
    void setup() {
        solicitacaoRepository = mock(SolicitacaoOnboardingRepository.class);
        documentoRepository = mock(DocumentoCadastralRepository.class);
        resultadoRepository = mock(ResultadoVerificacaoRepository.class);
        useCase = new ConsultarStatusOnboardingUseCase(solicitacaoRepository, documentoRepository, resultadoRepository);

        usuarioId = UUID.randomUUID();
        solicitacao =
                SolicitacaoOnboarding.criarPessoa(usuarioId, new Cpf("52998224725"), "Joao", LocalDate.of(1990, 1, 1));
        when(solicitacaoRepository.findById(any())).thenReturn(Optional.of(solicitacao));
        when(documentoRepository.findBySolicitacaoId(any()))
                .thenReturn(List.of(DocumentoCadastral.criar(
                        solicitacao.getId(), TipoDocumento.RG, new byte[] {1}, "image/jpeg", "rg.jpg", "h")));
        when(resultadoRepository.findBySolicitacaoId(any())).thenReturn(Optional.empty());
    }

    @Test
    void ownerLeProprioSolicitacao() {
        StatusOnboardingView view = useCase.executar(solicitacao.getId(), usuarioId, false);

        assertThat(view.id()).isEqualTo(solicitacao.getId());
        assertThat(view.status()).isEqualTo(StatusOnboarding.INICIADO);
        assertThat(view.documentosEnviados()).hasSize(1);
        assertThat(view.resultado()).isNull();
    }

    @Test
    void adminLeQualquerSolicitacao() {
        StatusOnboardingView view = useCase.executar(solicitacao.getId(), UUID.randomUUID(), true);

        assertThat(view.id()).isEqualTo(solicitacao.getId());
    }

    @Test
    void rejeitaUsuarioAlheioNaoAdmin() {
        assertThatThrownBy(() -> useCase.executar(solicitacao.getId(), UUID.randomUUID(), false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejeitaSolicitacaoInexistente() {
        when(solicitacaoRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID(), usuarioId, false))
                .isInstanceOf(OnboardingNaoEncontradoException.class);
    }

    @Test
    void mapeiaResultadoQuandoExiste() {
        when(resultadoRepository.findBySolicitacaoId(any()))
                .thenReturn(Optional.of(
                        ResultadoVerificacao.registrar(solicitacao.getId(), StatusOnboarding.APROVADO, "ok", "{}")));

        StatusOnboardingView view = useCase.executar(solicitacao.getId(), usuarioId, false);

        assertThat(view.resultado()).isNotNull();
        assertThat(view.resultado().statusFinal()).isEqualTo(StatusOnboarding.APROVADO);
    }
}
