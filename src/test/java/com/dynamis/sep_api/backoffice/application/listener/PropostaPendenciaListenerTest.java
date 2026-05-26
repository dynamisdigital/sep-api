package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.job.BackofficeVerificadorProperties;
import com.dynamis.sep_api.backoffice.application.port.out.PendenciaCreditoQueryPort;
import com.dynamis.sep_api.backoffice.application.port.out.dto.PropostaPendenciaView;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PropostaPendenciaListenerTest {

    @Test
    void verificar_criaItemPraCadaPropostaParada() {
        PendenciaCreditoQueryPort port = mock(PendenciaCreditoQueryPort.class);
        CriarItemFilaOperacionalService criarItem = mock(CriarItemFilaOperacionalService.class);
        BackofficeVerificadorProperties props = new BackofficeVerificadorProperties("0 */15 * * * *", 24, 48, 1);
        Clock clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        when(port.propostasParadasEmAnalise(any()))
                .thenReturn(List.of(new PropostaPendenciaView(p1), new PropostaPendenciaView(p2)));
        when(criarItem.criarSeAusente(any())).thenReturn(Optional.of(UUID.randomUUID()));

        new PropostaPendenciaListener(port, criarItem, props, clock).verificar();

        ArgumentCaptor<CriarItemCommand> captor = ArgumentCaptor.forClass(CriarItemCommand.class);
        verify(criarItem, times(2)).criarSeAusente(captor.capture());
        for (CriarItemCommand cmd : captor.getAllValues()) {
            assertThat(cmd.tipo()).isEqualTo(TipoItemFila.PROPOSTA_PENDENTE);
            assertThat(cmd.prioridade()).isEqualTo(PrioridadeItem.MEDIA);
        }
        assertThat(captor.getAllValues().stream().map(CriarItemCommand::entidadeId))
                .containsExactly(p1, p2);
    }

    @Test
    void verificar_listaVazia_naoCriaItens() {
        PendenciaCreditoQueryPort port = mock(PendenciaCreditoQueryPort.class);
        CriarItemFilaOperacionalService criarItem = mock(CriarItemFilaOperacionalService.class);
        BackofficeVerificadorProperties props = new BackofficeVerificadorProperties("0 */15 * * * *", 24, 48, 1);
        Clock clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);
        when(port.propostasParadasEmAnalise(any())).thenReturn(List.of());

        new PropostaPendenciaListener(port, criarItem, props, clock).verificar();

        verify(criarItem, never()).criarSeAusente(any());
    }
}
