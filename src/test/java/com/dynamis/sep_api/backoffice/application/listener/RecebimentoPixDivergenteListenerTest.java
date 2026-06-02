package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.pix.domain.event.PixRecebimentoDivergenteEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RecebimentoPixDivergenteListenerTest {

    private final CriarItemFilaOperacionalService criarItem = mock(CriarItemFilaOperacionalService.class);
    private final RecebimentoPixDivergenteListener listener = new RecebimentoPixDivergenteListener(criarItem);

    @Test
    void aoDivergir_criaItemRecebimentoPixDivergente() {
        UUID recebimentoId = UUID.randomUUID();

        listener.aoDivergir(new PixRecebimentoDivergenteEvent(recebimentoId, "Recebimento Pix sem referencia"));

        ArgumentCaptor<CriarItemCommand> captor = ArgumentCaptor.forClass(CriarItemCommand.class);
        verify(criarItem).criarSeAusente(captor.capture());
        CriarItemCommand cmd = captor.getValue();
        assertThat(cmd.tipo()).isEqualTo(TipoItemFila.RECEBIMENTO_PIX_DIVERGENTE);
        assertThat(cmd.tipoEntidade()).isEqualTo(TipoEntidadeReferenciada.PIX_RECEBIMENTO);
        assertThat(cmd.entidadeId()).isEqualTo(recebimentoId);
        assertThat(cmd.prioridade()).isEqualTo(PrioridadeItem.ALTA);
        assertThat(cmd.descricao()).isEqualTo("Recebimento Pix sem referencia");
    }

    @Test
    void aoDivergir_motivoNulo_usaFallback() {
        listener.aoDivergir(new PixRecebimentoDivergenteEvent(UUID.randomUUID(), null));

        ArgumentCaptor<CriarItemCommand> captor = ArgumentCaptor.forClass(CriarItemCommand.class);
        verify(criarItem).criarSeAusente(captor.capture());
        assertThat(captor.getValue().descricao()).isNotBlank();
    }
}
