package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.dto.ProcessarCallbackConsentimentoCommand;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceAutorizadoEvent;
import com.dynamis.sep_api.credito.domain.event.OpenFinanceNegadoEvent;
import com.dynamis.sep_api.credito.domain.exception.ConsentimentoNaoEncontradoException;
import com.dynamis.sep_api.credito.domain.model.ConsentimentoOpenFinance;
import com.dynamis.sep_api.credito.domain.vo.StatusConsentimento;
import com.dynamis.sep_api.credito.infrastructure.persistence.ConsentimentoOpenFinanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessarCallbackConsentimentoUseCaseTest {

    private ConsentimentoOpenFinanceRepository repository;
    private ApplicationEventPublisher publisher;
    private ProcessarCallbackConsentimentoUseCase useCase;

    @BeforeEach
    void setup() {
        repository = mock(ConsentimentoOpenFinanceRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        useCase = new ProcessarCallbackConsentimentoUseCase(repository, publisher);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ConsentimentoOpenFinance pendente() {
        return ConsentimentoOpenFinance.iniciar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "https://celcoin/auth",
                "ext-1",
                OffsetDateTime.now().plusDays(30));
    }

    @Test
    void callbackAutorizadoTransicionaParaAutorizado() {
        ConsentimentoOpenFinance c = pendente();
        when(repository.findByIdExternoCelcoin("ext-1")).thenReturn(Optional.of(c));

        useCase.executar(new ProcessarCallbackConsentimentoCommand("ext-1", true));

        assertThat(c.getStatus()).isEqualTo(StatusConsentimento.AUTORIZADO);
        verify(publisher).publishEvent(any(OpenFinanceAutorizadoEvent.class));
    }

    @Test
    void callbackNegadoTransicionaParaNegado() {
        ConsentimentoOpenFinance c = pendente();
        when(repository.findByIdExternoCelcoin("ext-1")).thenReturn(Optional.of(c));

        useCase.executar(new ProcessarCallbackConsentimentoCommand("ext-1", false));

        assertThat(c.getStatus()).isEqualTo(StatusConsentimento.NEGADO);
        verify(publisher).publishEvent(any(OpenFinanceNegadoEvent.class));
    }

    @Test
    void rejeita404QuandoConsentimentoNaoExiste() {
        when(repository.findByIdExternoCelcoin("ext-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(new ProcessarCallbackConsentimentoCommand("ext-x", true)))
                .isInstanceOf(ConsentimentoNaoEncontradoException.class);
    }

    @Test
    void callbackDuplicadoIdempotenteNaoRepublicaEvento() {
        ConsentimentoOpenFinance c = pendente();
        c.autorizar();
        when(repository.findByIdExternoCelcoin("ext-1")).thenReturn(Optional.of(c));

        useCase.executar(new ProcessarCallbackConsentimentoCommand("ext-1", true));

        verify(publisher, never()).publishEvent(any());
        verify(repository, never()).save(any());
    }

    @Test
    void callbackNegadoAposAutorizadoRevogaEPublicaRevogadoEvent() {
        // Sprint 15 — 15F-019: revogacao tardia. Provider e source-of-truth; quando detentor
        // revoga consentimento via app do banco apos AUTORIZADO, o agregado aceita transicao
        // AUTORIZADO -> NEGADO e publica OpenFinanceRevogadoEvent (semantica distinta de
        // OpenFinanceNegadoEvent emitido a partir de PENDENTE).
        ConsentimentoOpenFinance c = pendente();
        c.autorizar();
        when(repository.findByIdExternoCelcoin("ext-1")).thenReturn(Optional.of(c));

        useCase.executar(new ProcessarCallbackConsentimentoCommand("ext-1", false));

        assertThat(c.getStatus()).isEqualTo(StatusConsentimento.NEGADO);
        verify(repository).save(c);
        verify(publisher).publishEvent(any(com.dynamis.sep_api.credito.domain.event.OpenFinanceRevogadoEvent.class));
    }

    @Test
    void callbackConflitanteAposNegadoNaoReverteEPublica() {
        // NEGADO/EXPIRADO sao terminais — callback AUTORIZADO tardio nao reverte.
        ConsentimentoOpenFinance c = pendente();
        c.negar();
        when(repository.findByIdExternoCelcoin("ext-1")).thenReturn(Optional.of(c));

        useCase.executar(new ProcessarCallbackConsentimentoCommand("ext-1", true));

        assertThat(c.getStatus()).isEqualTo(StatusConsentimento.NEGADO);
        verify(publisher, never()).publishEvent(any());
        verify(repository, never()).save(any());
    }
}
