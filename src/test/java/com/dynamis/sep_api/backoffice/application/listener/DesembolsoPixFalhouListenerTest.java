package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.pix.domain.event.PixTransferenciaFalhouEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DesembolsoPixFalhouListenerTest {

    private final CriarItemFilaOperacionalService criarItem = mock(CriarItemFilaOperacionalService.class);
    private final DesembolsoPixFalhouListener listener = new DesembolsoPixFalhouListener(criarItem);

    @Test
    void aoFalhar_criaItemDesembolsoPixFalhou() {
        UUID transferenciaId = UUID.randomUUID();
        UUID contratoId = UUID.randomUUID();

        listener.aoFalhar(new PixTransferenciaFalhouEvent(
                transferenciaId, contratoId, UUID.randomUUID(), "Falha tecnica no provider"));

        ArgumentCaptor<CriarItemCommand> captor = ArgumentCaptor.forClass(CriarItemCommand.class);
        verify(criarItem).criarSeAusente(captor.capture());
        CriarItemCommand cmd = captor.getValue();
        assertThat(cmd.tipo()).isEqualTo(TipoItemFila.DESEMBOLSO_PIX_FALHOU);
        assertThat(cmd.tipoEntidade()).isEqualTo(TipoEntidadeReferenciada.PIX_TRANSFERENCIA);
        assertThat(cmd.entidadeId()).isEqualTo(transferenciaId);
        assertThat(cmd.prioridade()).isEqualTo(PrioridadeItem.ALTA);
    }

    @Test
    void aoFalhar_motivoNulo_usaFallback() {
        listener.aoFalhar(
                new PixTransferenciaFalhouEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null));

        ArgumentCaptor<CriarItemCommand> captor = ArgumentCaptor.forClass(CriarItemCommand.class);
        verify(criarItem).criarSeAusente(captor.capture());
        assertThat(captor.getValue().descricao()).isNotBlank();
    }
}
