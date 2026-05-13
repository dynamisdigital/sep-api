package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.application.dto.DocumentoUploadCommand;
import com.dynamis.sep_api.onboarding.application.port.out.DocumentoStorage;
import com.dynamis.sep_api.onboarding.domain.event.DocumentoCadastralEnviadoEvent;
import com.dynamis.sep_api.onboarding.domain.exception.OnboardingNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.model.DocumentoCadastral;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnviarDocumentoUseCaseTest {

    private SolicitacaoOnboardingRepository solicitacaoRepository;
    private DocumentoStorage storage;
    private ApplicationEventPublisher eventPublisher;
    private EnviarDocumentoUseCase useCase;

    private UUID usuarioId;
    private SolicitacaoOnboarding solicitacao;

    @BeforeEach
    void setup() {
        solicitacaoRepository = mock(SolicitacaoOnboardingRepository.class);
        storage = mock(DocumentoStorage.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new EnviarDocumentoUseCase(solicitacaoRepository, storage, eventPublisher);

        usuarioId = UUID.randomUUID();
        solicitacao = SolicitacaoOnboarding.criar(usuarioId, new Cpf("52998224725"), "Joao", LocalDate.of(1990, 1, 1));
        when(solicitacaoRepository.findById(any())).thenReturn(Optional.of(solicitacao));
        when(solicitacaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(storage.salvar(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void primeiroUploadTransicionaParaDocumentosRecebidosEPublicaEvento() {
        DocumentoUploadCommand cmd =
                new DocumentoUploadCommand(TipoDocumento.RG, "image/jpeg", "rg.jpg", new byte[] {1, 2, 3});

        DocumentoCadastral salvo = useCase.executar(solicitacao.getId(), usuarioId, false, cmd);

        assertThat(solicitacao.getStatus()).isEqualTo(StatusOnboarding.DOCUMENTOS_RECEBIDOS);
        assertThat(salvo.getSha256()).hasSize(64);
        verify(eventPublisher).publishEvent(any(DocumentoCadastralEnviadoEvent.class));
    }

    @Test
    void rejeitaMimeNaoSuportado() {
        DocumentoUploadCommand cmd =
                new DocumentoUploadCommand(TipoDocumento.RG, "application/octet-stream", "rg.bin", new byte[] {1});

        assertThatThrownBy(() -> useCase.executar(solicitacao.getId(), usuarioId, false, cmd))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("MIME");
    }

    @Test
    void rejeitaTamanhoAcimaDe10MB() {
        byte[] grande = new byte[10 * 1024 * 1024 + 1];
        DocumentoUploadCommand cmd = new DocumentoUploadCommand(TipoDocumento.RG, "image/jpeg", "rg.jpg", grande);

        assertThatThrownBy(() -> useCase.executar(solicitacao.getId(), usuarioId, false, cmd))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("tamanho");
    }

    @Test
    void rejeitaConteudoVazio() {
        DocumentoUploadCommand cmd = new DocumentoUploadCommand(TipoDocumento.RG, "image/jpeg", "rg.jpg", new byte[0]);

        assertThatThrownBy(() -> useCase.executar(solicitacao.getId(), usuarioId, false, cmd))
                .isInstanceOf(ValidacaoException.class);
    }

    @Test
    void rejeitaUsuarioNaoOwner() {
        UUID outro = UUID.randomUUID();
        DocumentoUploadCommand cmd =
                new DocumentoUploadCommand(TipoDocumento.RG, "image/jpeg", "rg.jpg", new byte[] {1});

        assertThatThrownBy(() -> useCase.executar(solicitacao.getId(), outro, false, cmd))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejeitaSolicitacaoInexistente() {
        when(solicitacaoRepository.findById(any())).thenReturn(Optional.empty());
        DocumentoUploadCommand cmd =
                new DocumentoUploadCommand(TipoDocumento.RG, "image/jpeg", "rg.jpg", new byte[] {1});

        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID(), usuarioId, false, cmd))
                .isInstanceOf(OnboardingNaoEncontradoException.class);
    }

    @Test
    void adminAnexaDocumentoEmSolicitacaoDeTerceiro() {
        UUID adminId = UUID.randomUUID();
        DocumentoUploadCommand cmd =
                new DocumentoUploadCommand(TipoDocumento.RG, "image/jpeg", "rg.jpg", new byte[] {1, 2, 3});

        DocumentoCadastral salvo = useCase.executar(solicitacao.getId(), adminId, true, cmd);

        assertThat(salvo).isNotNull();
        assertThat(solicitacao.getStatus()).isEqualTo(StatusOnboarding.DOCUMENTOS_RECEBIDOS);
    }
}
