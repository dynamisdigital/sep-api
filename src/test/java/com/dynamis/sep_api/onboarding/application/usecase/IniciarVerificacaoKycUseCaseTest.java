package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.application.port.out.KycProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RequisicaoVerificacaoKyc;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaInicioVerificacao;
import com.dynamis.sep_api.onboarding.domain.event.VerificacaoKycDisparadaEvent;
import com.dynamis.sep_api.onboarding.domain.exception.OnboardingNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.model.DocumentoCadastral;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.DocumentoCadastralRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IniciarVerificacaoKycUseCaseTest {

    private SolicitacaoOnboardingRepository solicitacaoRepository;
    private DocumentoCadastralRepository documentoRepository;
    private KycProvider kycProvider;
    private ApplicationEventPublisher eventPublisher;
    private IniciarVerificacaoKycUseCase useCase;

    private UUID usuarioId;
    private SolicitacaoOnboarding solicitacao;

    @BeforeEach
    void setup() {
        solicitacaoRepository = mock(SolicitacaoOnboardingRepository.class);
        documentoRepository = mock(DocumentoCadastralRepository.class);
        kycProvider = mock(KycProvider.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new IniciarVerificacaoKycUseCase(
                solicitacaoRepository, documentoRepository, kycProvider, eventPublisher);

        usuarioId = UUID.randomUUID();
        solicitacao = SolicitacaoOnboarding.criar(usuarioId, new Cpf("52998224725"), "Joao", LocalDate.of(1990, 1, 1));
        solicitacao.registrarDocumentoEnviado(); // DOCUMENTOS_RECEBIDOS

        when(solicitacaoRepository.findById(any())).thenReturn(Optional.of(solicitacao));
        when(solicitacaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(kycProvider.iniciarVerificacao(any(), anyString()))
                .thenReturn(new RespostaInicioVerificacao("ext-1", "PROCESSING"));
    }

    private DocumentoCadastral doc(TipoDocumento tipo) {
        return DocumentoCadastral.criar(solicitacao.getId(), tipo, new byte[] {1}, "image/jpeg", "f.jpg", "h-" + tipo);
    }

    @Test
    void disparaVerificacaoQuandoDocumentosMinimosPresentes() {
        when(documentoRepository.findBySolicitacaoId(any()))
                .thenReturn(List.of(doc(TipoDocumento.RG), doc(TipoDocumento.SELFIE)));

        SolicitacaoOnboarding resultado = useCase.executar(solicitacao.getId(), usuarioId, "corr-1");

        assertThat(resultado.getStatus()).isEqualTo(StatusOnboarding.EM_VERIFICACAO);
        assertThat(resultado.getIdVerificacaoExterna()).isEqualTo("ext-1");
        verify(eventPublisher).publishEvent(any(VerificacaoKycDisparadaEvent.class));
    }

    @Test
    void chaveIdempotenciaInclueRevisaoDocumentos() {
        when(documentoRepository.findBySolicitacaoId(any()))
                .thenReturn(List.of(doc(TipoDocumento.RG), doc(TipoDocumento.SELFIE)));

        useCase.executar(solicitacao.getId(), usuarioId, "corr-1");

        ArgumentCaptor<RequisicaoVerificacaoKyc> captor = ArgumentCaptor.forClass(RequisicaoVerificacaoKyc.class);
        verify(kycProvider).iniciarVerificacao(captor.capture(), anyString());
        assertThat(captor.getValue().solicitacaoId()).isEqualTo(solicitacao.getId());
        assertThat(captor.getValue().documentos()).hasSize(2);
    }

    @Test
    void rejeitaSemSelfie() {
        when(documentoRepository.findBySolicitacaoId(any())).thenReturn(List.of(doc(TipoDocumento.RG)));

        assertThatThrownBy(() -> useCase.executar(solicitacao.getId(), usuarioId, "corr-1"))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("SELFIE");
    }

    @Test
    void rejeitaSemDocumentoIdentidade() {
        when(documentoRepository.findBySolicitacaoId(any())).thenReturn(List.of(doc(TipoDocumento.SELFIE)));

        assertThatThrownBy(() -> useCase.executar(solicitacao.getId(), usuarioId, "corr-1"))
                .isInstanceOf(ValidacaoException.class);
    }

    @Test
    void rejeitaUsuarioNaoOwner() {
        when(documentoRepository.findBySolicitacaoId(any()))
                .thenReturn(List.of(doc(TipoDocumento.RG), doc(TipoDocumento.SELFIE)));

        assertThatThrownBy(() -> useCase.executar(solicitacao.getId(), UUID.randomUUID(), "corr-1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejeitaSolicitacaoInexistente() {
        when(solicitacaoRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID(), usuarioId, "corr-1"))
                .isInstanceOf(OnboardingNaoEncontradoException.class);
    }
}
