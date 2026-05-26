package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.job.BackofficeVerificadorProperties;
import com.dynamis.sep_api.backoffice.application.port.out.PendenciaContratoQueryPort;
import com.dynamis.sep_api.backoffice.application.port.out.dto.ContratoPendenciaView;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContratoSemAssinaturaListenerTest {

    @Test
    void verificar_criaItemPraCadaContratoParado() {
        PendenciaContratoQueryPort port = mock(PendenciaContratoQueryPort.class);
        CriarItemFilaOperacionalService criarItem = mock(CriarItemFilaOperacionalService.class);
        BackofficeVerificadorProperties props = new BackofficeVerificadorProperties("0 */15 * * * *", 24, 48, 1);
        Clock clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);
        UUID c1 = UUID.randomUUID();
        when(port.contratosAceitosSemAssinatura(any())).thenReturn(List.of(new ContratoPendenciaView(c1)));
        when(criarItem.criarSeAusente(any())).thenReturn(Optional.of(UUID.randomUUID()));

        new ContratoSemAssinaturaListener(port, criarItem, props, clock).verificar();

        ArgumentCaptor<CriarItemCommand> captor = ArgumentCaptor.forClass(CriarItemCommand.class);
        verify(criarItem).criarSeAusente(captor.capture());
        CriarItemCommand cmd = captor.getValue();
        assertThat(cmd.tipo()).isEqualTo(TipoItemFila.CONTRATO_NAO_ASSINADO);
        assertThat(cmd.prioridade()).isEqualTo(PrioridadeItem.MEDIA);
        assertThat(cmd.entidadeId()).isEqualTo(c1);
    }
}
