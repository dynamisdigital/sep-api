package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.job.BackofficeVerificadorProperties;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PropostaPendenciaListenerTest {

    @Test
    void verificar_criaItemPraCadaPropostaParada() {
        PropostaCreditoRepository repo = mock(PropostaCreditoRepository.class);
        CriarItemFilaOperacionalService criarItem = mock(CriarItemFilaOperacionalService.class);
        BackofficeVerificadorProperties props = new BackofficeVerificadorProperties("0 */15 * * * *", 24, 48, 1);
        Clock clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);

        PropostaCredito p1 = PropostaCredito.criar(UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("1000"), 6);
        PropostaCredito p2 = PropostaCredito.criar(UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("2000"), 12);
        when(repo.findByStatusAndDataModificacaoBefore(eq(StatusProposta.EM_ANALISE), any()))
                .thenReturn(List.of(p1, p2));
        when(criarItem.criarSeAusente(any())).thenReturn(Optional.of(UUID.randomUUID()));

        new PropostaPendenciaListener(repo, criarItem, props, clock).verificar();

        ArgumentCaptor<CriarItemCommand> captor = ArgumentCaptor.forClass(CriarItemCommand.class);
        verify(criarItem, org.mockito.Mockito.times(2)).criarSeAusente(captor.capture());
        for (CriarItemCommand cmd : captor.getAllValues()) {
            assertThat(cmd.tipo()).isEqualTo(TipoItemFila.PROPOSTA_PENDENTE);
            assertThat(cmd.prioridade()).isEqualTo(PrioridadeItem.MEDIA);
        }
    }

    @Test
    void verificar_listaVazia_naoCriaItens() {
        PropostaCreditoRepository repo = mock(PropostaCreditoRepository.class);
        CriarItemFilaOperacionalService criarItem = mock(CriarItemFilaOperacionalService.class);
        BackofficeVerificadorProperties props = new BackofficeVerificadorProperties("0 */15 * * * *", 24, 48, 1);
        Clock clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);
        when(repo.findByStatusAndDataModificacaoBefore(any(), any())).thenReturn(List.of());

        new PropostaPendenciaListener(repo, criarItem, props, clock).verificar();

        verify(criarItem, org.mockito.Mockito.never()).criarSeAusente(any());
    }
}
