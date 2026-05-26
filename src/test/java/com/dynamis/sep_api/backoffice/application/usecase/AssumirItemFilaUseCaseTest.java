package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.domain.event.ItemAssumidoEvent;
import com.dynamis.sep_api.backoffice.domain.exception.ItemFilaNaoEncontradoException;
import com.dynamis.sep_api.backoffice.domain.exception.TransicaoItemInvalidaException;
import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssumirItemFilaUseCaseTest {

    private ItemFilaOperacionalRepository repository;
    private ApplicationEventPublisher publisher;
    private AssumirItemFilaUseCase useCase;

    @BeforeEach
    void setup() {
        repository = mock(ItemFilaOperacionalRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);
        useCase = new AssumirItemFilaUseCase(repository, publisher, clock);
        when(repository.save(any(ItemFilaOperacional.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void happy_atribuiETransiciona() {
        ItemFilaOperacional item = novoItemAberto();
        when(repository.findById(item.getId())).thenReturn(Optional.of(item));
        UUID operador = UUID.randomUUID();

        ItemFilaOperacional resultado = useCase.executar(item.getId(), operador);

        assertThat(resultado.getStatus()).isEqualTo(StatusItemFila.EM_TRATAMENTO);
        assertThat(resultado.getAtribuidoA()).isEqualTo(operador);
        verify(publisher, times(1)).publishEvent(any(ItemAssumidoEvent.class));
    }

    @Test
    void naoEncontrado_lanca404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ItemFilaNaoEncontradoException.class)
                .isThrownBy(() -> useCase.executar(id, UUID.randomUUID()));
    }

    @Test
    void transicaoInvalida_lanca409() {
        ItemFilaOperacional item = novoItemAberto();
        item.assumir(UUID.randomUUID());
        when(repository.findById(item.getId())).thenReturn(Optional.of(item));

        assertThatExceptionOfType(TransicaoItemInvalidaException.class)
                .isThrownBy(() -> useCase.executar(item.getId(), UUID.randomUUID()));
    }

    private ItemFilaOperacional novoItemAberto() {
        return ItemFilaOperacional.abrir(
                TipoItemFila.ONBOARDING_ERRO,
                PrioridadeItem.ALTA,
                TipoEntidadeReferenciada.ONBOARDING,
                UUID.randomUUID(),
                "Onboarding REPROVADO",
                null,
                OffsetDateTime.now());
    }
}
