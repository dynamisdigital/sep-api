package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaInadimplenteEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParcelaInadimplenteListenerTest {

    @Test
    void aoMarcarInadimplente_criaItemCobrancaInadimplenteAlta() {
        CriarItemFilaOperacionalService criarItem = mock(CriarItemFilaOperacionalService.class);
        when(criarItem.criarSeAusente(any())).thenReturn(Optional.of(UUID.randomUUID()));
        ParcelaInadimplenteListener listener = new ParcelaInadimplenteListener(criarItem);

        UUID parcela = UUID.randomUUID();
        UUID agenda = UUID.randomUUID();
        UUID contrato = UUID.randomUUID();
        UUID tomador = UUID.randomUUID();

        listener.aoMarcarInadimplente(
                new ParcelaInadimplenteEvent(parcela, agenda, contrato, tomador, 3, LocalDate.of(2026, 2, 25), 90));

        ArgumentCaptor<CriarItemCommand> captor = ArgumentCaptor.forClass(CriarItemCommand.class);
        verify(criarItem).criarSeAusente(captor.capture());
        CriarItemCommand cmd = captor.getValue();
        assertThat(cmd.tipo()).isEqualTo(TipoItemFila.COBRANCA_INADIMPLENTE);
        assertThat(cmd.prioridade()).isEqualTo(PrioridadeItem.ALTA);
        assertThat(cmd.tipoEntidade()).isEqualTo(TipoEntidadeReferenciada.PARCELA_COBRANCA);
        assertThat(cmd.entidadeId()).isEqualTo(parcela);
        assertThat(cmd.titulo()).contains("90 dias");
    }
}
