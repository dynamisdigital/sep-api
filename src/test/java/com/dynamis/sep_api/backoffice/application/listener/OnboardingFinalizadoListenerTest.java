package com.dynamis.sep_api.backoffice.application.listener;

import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService;
import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.onboarding.domain.event.OnboardingFinalizadoEvent;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnboardingFinalizadoListenerTest {

    private CriarItemFilaOperacionalService criarItem;
    private OnboardingFinalizadoListener listener;

    @BeforeEach
    void setup() {
        criarItem = mock(CriarItemFilaOperacionalService.class);
        when(criarItem.criarSeAusente(any())).thenReturn(Optional.of(UUID.randomUUID()));
        listener = new OnboardingFinalizadoListener(criarItem);
    }

    @Test
    void reprovado_geraItemErroAlta() {
        UUID solicitacao = UUID.randomUUID();
        listener.aoFinalizar(
                new OnboardingFinalizadoEvent(solicitacao, UUID.randomUUID(), StatusOnboarding.REPROVADO, "ext-1"));

        CriarItemCommand cmd = capturar();
        assertThat(cmd.tipo()).isEqualTo(TipoItemFila.ONBOARDING_ERRO);
        assertThat(cmd.prioridade()).isEqualTo(PrioridadeItem.ALTA);
        assertThat(cmd.tipoEntidade()).isEqualTo(TipoEntidadeReferenciada.ONBOARDING);
        assertThat(cmd.entidadeId()).isEqualTo(solicitacao);
    }

    @Test
    void reprovadoPld_geraItemErroAlta() {
        listener.aoFinalizar(new OnboardingFinalizadoEvent(
                UUID.randomUUID(), UUID.randomUUID(), StatusOnboarding.REPROVADO_PLD, "ext-2"));

        CriarItemCommand cmd = capturar();
        assertThat(cmd.tipo()).isEqualTo(TipoItemFila.ONBOARDING_ERRO);
        assertThat(cmd.prioridade()).isEqualTo(PrioridadeItem.ALTA);
    }

    @Test
    void pendencia_geraItemPendenteMedia() {
        listener.aoFinalizar(new OnboardingFinalizadoEvent(
                UUID.randomUUID(), UUID.randomUUID(), StatusOnboarding.PENDENCIA, "ext-3"));

        CriarItemCommand cmd = capturar();
        assertThat(cmd.tipo()).isEqualTo(TipoItemFila.ONBOARDING_PENDENTE);
        assertThat(cmd.prioridade()).isEqualTo(PrioridadeItem.MEDIA);
    }

    @Test
    void aprovado_ignorado() {
        listener.aoFinalizar(new OnboardingFinalizadoEvent(
                UUID.randomUUID(), UUID.randomUUID(), StatusOnboarding.APROVADO_FINAL, "ext-4"));

        verify(criarItem, never()).criarSeAusente(any());
    }

    @Test
    void exception_noServico_naoPropagaPraEventBus() {
        when(criarItem.criarSeAusente(any())).thenThrow(new RuntimeException("repo offline"));

        listener.aoFinalizar(new OnboardingFinalizadoEvent(
                UUID.randomUUID(), UUID.randomUUID(), StatusOnboarding.PENDENCIA, "ext-5"));
    }

    private CriarItemCommand capturar() {
        ArgumentCaptor<CriarItemCommand> captor = ArgumentCaptor.forClass(CriarItemCommand.class);
        verify(criarItem).criarSeAusente(captor.capture());
        return captor.getValue();
    }
}
