package com.dynamis.sep_api.backoffice.application.service;

import com.dynamis.sep_api.backoffice.domain.exception.LimiteReprocessoExcedidoException;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ReprocessoRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AntiAbusoReprocessoServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void zeroReprocessos_naoLanca() {
        ReprocessoRepository repo = mock(ReprocessoRepository.class);
        when(repo.countByIdentificadorExternoAndDataDisparoAfter(any(), any())).thenReturn(0L);
        AntiAbusoReprocessoService service = new AntiAbusoReprocessoService(repo, clock);

        assertThatCode(() -> service.validarLimite("X")).doesNotThrowAnyException();
    }

    @Test
    void doisReprocessos_naoLanca() {
        ReprocessoRepository repo = mock(ReprocessoRepository.class);
        when(repo.countByIdentificadorExternoAndDataDisparoAfter(any(), any())).thenReturn(2L);
        AntiAbusoReprocessoService service = new AntiAbusoReprocessoService(repo, clock);

        assertThatCode(() -> service.validarLimite("X")).doesNotThrowAnyException();
    }

    @Test
    void tresReprocessos_lanca() {
        ReprocessoRepository repo = mock(ReprocessoRepository.class);
        when(repo.countByIdentificadorExternoAndDataDisparoAfter(any(), any())).thenReturn(3L);
        AntiAbusoReprocessoService service = new AntiAbusoReprocessoService(repo, clock);

        assertThatExceptionOfType(LimiteReprocessoExcedidoException.class).isThrownBy(() -> service.validarLimite("X"));
    }

    @Test
    void quatroReprocessos_lanca() {
        ReprocessoRepository repo = mock(ReprocessoRepository.class);
        when(repo.countByIdentificadorExternoAndDataDisparoAfter(eq("Y"), any()))
                .thenReturn(4L);
        AntiAbusoReprocessoService service = new AntiAbusoReprocessoService(repo, clock);

        assertThatExceptionOfType(LimiteReprocessoExcedidoException.class).isThrownBy(() -> service.validarLimite("Y"));
    }

    @Test
    void usaJanelaDe24h() {
        ReprocessoRepository repo = mock(ReprocessoRepository.class);
        when(repo.countByIdentificadorExternoAndDataDisparoAfter(any(), any())).thenReturn(0L);
        AntiAbusoReprocessoService service = new AntiAbusoReprocessoService(repo, clock);

        service.validarLimite(UUID.randomUUID().toString());
        // delega contagem ao repo com corte = now-24h; teste de janela exato fica no repo IT
    }
}
