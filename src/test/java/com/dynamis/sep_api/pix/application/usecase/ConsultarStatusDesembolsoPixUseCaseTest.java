package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.ConsultarStatusDesembolsoPixCommand;
import com.dynamis.sep_api.pix.application.dto.StatusDesembolsoPixResult;
import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.StatusTransferenciaPixProvider;
import com.dynamis.sep_api.pix.application.port.out.exception.PixProviderException;
import com.dynamis.sep_api.pix.application.service.SincronizadorStatusTransferencia;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultarStatusDesembolsoPixUseCaseTest {

    private PixTransferenciaRepository repository;
    private PixProvider pixProvider;
    private ConsultarStatusDesembolsoPixUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(PixTransferenciaRepository.class);
        pixProvider = mock(PixProvider.class);
        SincronizadorStatusTransferencia sincronizador =
                new SincronizadorStatusTransferencia(mock(ApplicationEventPublisher.class));
        useCase = new ConsultarStatusDesembolsoPixUseCase(repository, pixProvider, sincronizador);
    }

    private PixTransferencia desembolso() {
        return PixTransferencia.criarDesembolso(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10000.00"),
                "a".repeat(64),
                "us****om",
                "idem-1",
                "corr-1");
    }

    private ConsultarStatusDesembolsoPixCommand comando(UUID id) {
        return new ConsultarStatusDesembolsoPixCommand(id, "corr-1", true);
    }

    private ConsultarStatusDesembolsoPixCommand comandoLocal(UUID id) {
        return new ConsultarStatusDesembolsoPixCommand(id, "corr-1", false);
    }

    @Test
    void transferenciaInexistente_naoEncontrado() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(comando(id))).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void statusTerminal_naoConsultaProvider() {
        PixTransferencia t = desembolso();
        t.marcarSolicitada("ext-1");
        t.marcarConcluida();
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));

        StatusDesembolsoPixResult res = useCase.executar(comando(t.getId()));

        assertThat(res.status()).isEqualTo(StatusPixTransferencia.CONCLUIDA);
        verify(pixProvider, never()).consultarTransferencia(any(), any());
    }

    @Test
    void semExternalId_naoConsultaProvider() {
        PixTransferencia t = desembolso(); // CRIADA, sem externalId
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));

        StatusDesembolsoPixResult res = useCase.executar(comando(t.getId()));

        assertThat(res.status()).isEqualTo(StatusPixTransferencia.CRIADA);
        verify(pixProvider, never()).consultarTransferencia(any(), any());
    }

    @Test
    void solicitada_providerConcluida_avancaParaConcluida() {
        PixTransferencia t = desembolso();
        t.marcarSolicitada("ext-1");
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));
        when(pixProvider.consultarTransferencia("ext-1", "corr-1"))
                .thenReturn(new RespostaTransferenciaPix("ext-1", StatusTransferenciaPixProvider.CONCLUIDA));

        StatusDesembolsoPixResult res = useCase.executar(comando(t.getId()));

        assertThat(res.status()).isEqualTo(StatusPixTransferencia.CONCLUIDA);
        verify(repository).save(t);
    }

    @Test
    void solicitada_providerPendente_permaneceSolicitada() {
        PixTransferencia t = desembolso();
        t.marcarSolicitada("ext-1");
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));
        when(pixProvider.consultarTransferencia("ext-1", "corr-1"))
                .thenReturn(new RespostaTransferenciaPix("ext-1", StatusTransferenciaPixProvider.PENDENTE));

        StatusDesembolsoPixResult res = useCase.executar(comando(t.getId()));

        assertThat(res.status()).isEqualTo(StatusPixTransferencia.SOLICITADA);
    }

    @Test
    void providerIndisponivel_devolveStatusLocalSemFalhar() {
        PixTransferencia t = desembolso();
        t.marcarSolicitada("ext-1");
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));
        when(pixProvider.consultarTransferencia(any(), any())).thenThrow(new PixProviderException("timeout"));

        StatusDesembolsoPixResult res = useCase.executar(comando(t.getId()));

        assertThat(res.status()).isEqualTo(StatusPixTransferencia.SOLICITADA);
        assertThat(res.providerIndisponivel()).isTrue();
        assertThat(res.providerConsultado()).isFalse();
    }

    @Test
    void consultaComSucesso_providerConsultadoTrue() {
        PixTransferencia t = desembolso();
        t.marcarSolicitada("ext-1");
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));
        when(pixProvider.consultarTransferencia("ext-1", "corr-1"))
                .thenReturn(new RespostaTransferenciaPix("ext-1", StatusTransferenciaPixProvider.PROCESSANDO));

        StatusDesembolsoPixResult res = useCase.executar(comando(t.getId()));

        assertThat(res.providerConsultado()).isTrue();
        assertThat(res.providerIndisponivel()).isFalse();
    }

    @Test
    void leituraLocal_naoConsultaProvider() {
        PixTransferencia t = desembolso();
        t.marcarSolicitada("ext-1");
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));

        StatusDesembolsoPixResult res = useCase.executar(comandoLocal(t.getId()));

        assertThat(res.providerConsultado()).isFalse();
        assertThat(res.providerIndisponivel()).isFalse();
        verify(pixProvider, never()).consultarTransferencia(any(), any());
    }
}
