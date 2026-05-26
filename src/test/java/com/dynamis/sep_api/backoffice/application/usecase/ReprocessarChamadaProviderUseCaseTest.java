package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.application.port.out.ProviderReprocessadorPort;
import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.backoffice.application.service.AntiAbusoReprocessoService;
import com.dynamis.sep_api.backoffice.domain.event.ReprocessoDisparadoEvent;
import com.dynamis.sep_api.backoffice.domain.exception.LimiteReprocessoExcedidoException;
import com.dynamis.sep_api.backoffice.domain.model.Reprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.StatusReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ReprocessoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReprocessarChamadaProviderUseCaseTest {

    private ReprocessoRepository repo;
    private ProviderReprocessadorPort provider;
    private AntiAbusoReprocessoService antiAbuso;
    private ApplicationEventPublisher publisher;
    private ReprocessarChamadaProviderUseCase useCase;

    @BeforeEach
    void setup() {
        repo = mock(ReprocessoRepository.class);
        provider = mock(ProviderReprocessadorPort.class);
        antiAbuso = mock(AntiAbusoReprocessoService.class);
        publisher = mock(ApplicationEventPublisher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);
        useCase = new ReprocessarChamadaProviderUseCase(repo, provider, antiAbuso, publisher, clock);
        when(repo.save(any(Reprocesso.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void happy_dispatcha() {
        UUID entidade = UUID.randomUUID();
        when(provider.reprocessar(TipoChamadaProvider.KYC, entidade))
                .thenReturn(ResultadoReprocesso.sucesso("OK"));

        Reprocesso r = useCase.executar(TipoChamadaProvider.KYC, entidade, UUID.randomUUID(), null);

        assertThat(r.getStatus()).isEqualTo(StatusReprocesso.SUCESSO);
        assertThat(r.getTipoChamada()).isEqualTo(TipoChamadaProvider.KYC);
        verify(publisher).publishEvent(any(ReprocessoDisparadoEvent.class));
    }

    @Test
    void antiAbuso_lanca429() {
        UUID entidade = UUID.randomUUID();
        doThrow(new LimiteReprocessoExcedidoException(entidade.toString())).when(antiAbuso).validarLimite(any());

        assertThatExceptionOfType(LimiteReprocessoExcedidoException.class)
                .isThrownBy(() ->
                        useCase.executar(TipoChamadaProvider.PLD, entidade, UUID.randomUUID(), null));
        verify(repo, never()).save(any(Reprocesso.class));
    }

    @Test
    void tipoInvalido_propagaTipoReprocessoNaoSuportado() {
        UUID entidade = UUID.randomUUID();
        when(provider.reprocessar(any(), any()))
                .thenThrow(new com.dynamis.sep_api.backoffice.domain.exception.TipoReprocessoNaoSuportadoException(
                        TipoChamadaProvider.KYB));

        assertThatExceptionOfType(com.dynamis.sep_api.backoffice.domain.exception.TipoReprocessoNaoSuportadoException.class)
                .isThrownBy(() ->
                        useCase.executar(TipoChamadaProvider.KYB, entidade, UUID.randomUUID(), null));
    }
}
