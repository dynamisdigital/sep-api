package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.domain.event.ItemResolvidoEvent;
import com.dynamis.sep_api.backoffice.domain.exception.ItemFilaNaoEncontradoException;
import com.dynamis.sep_api.backoffice.domain.exception.JustificativaInvalidaException;
import com.dynamis.sep_api.backoffice.domain.exception.TransicaoItemInvalidaException;
import com.dynamis.sep_api.backoffice.domain.model.ComentarioInterno;
import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ComentarioInternoRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarcarItemResolvidoUseCaseTest {

    private ItemFilaOperacionalRepository itemRepo;
    private ComentarioInternoRepository comentarioRepo;
    private ApplicationEventPublisher publisher;
    private MarcarItemResolvidoUseCase useCase;

    @BeforeEach
    void setup() {
        itemRepo = mock(ItemFilaOperacionalRepository.class);
        comentarioRepo = mock(ComentarioInternoRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);
        useCase = new MarcarItemResolvidoUseCase(itemRepo, comentarioRepo, publisher, clock);
        when(itemRepo.save(any(ItemFilaOperacional.class))).thenAnswer(inv -> inv.getArgument(0));
        when(comentarioRepo.save(any(ComentarioInterno.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void happy_transicionaPersisteJustificativa() {
        ItemFilaOperacional item = emTratamento();
        when(itemRepo.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));

        ItemFilaOperacional resultado = useCase.executar(
                item.getId(), UUID.randomUUID(), "Operador validou documentos manualmente");

        assertThat(resultado.getStatus()).isEqualTo(StatusItemFila.RESOLVIDO);
        verify(comentarioRepo).save(any(ComentarioInterno.class));
        verify(publisher).publishEvent(any(ItemResolvidoEvent.class));
    }

    @Test
    void justificativaCurta_lanca400() {
        assertThatExceptionOfType(JustificativaInvalidaException.class)
                .isThrownBy(() -> useCase.executar(UUID.randomUUID(), UUID.randomUUID(), "curta"));
    }

    @Test
    void itemNaoExiste_lanca404() {
        UUID id = UUID.randomUUID();
        when(itemRepo.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ItemFilaNaoEncontradoException.class)
                .isThrownBy(() -> useCase.executar(id, UUID.randomUUID(), "Justificativa com mais de vinte caracteres"));
    }

    @Test
    void transicaoInvalidaQuandoNaoEstaEmTratamento_lanca409() {
        ItemFilaOperacional item = novoAberto();
        when(itemRepo.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));

        assertThatExceptionOfType(TransicaoItemInvalidaException.class)
                .isThrownBy(() -> useCase.executar(item.getId(), UUID.randomUUID(), "Justificativa com mais de vinte caracteres"));
    }

    private ItemFilaOperacional novoAberto() {
        return ItemFilaOperacional.abrir(
                TipoItemFila.ONBOARDING_ERRO,
                PrioridadeItem.ALTA,
                TipoEntidadeReferenciada.ONBOARDING,
                UUID.randomUUID(),
                "Onboarding REPROVADO",
                null,
                OffsetDateTime.now());
    }

    private ItemFilaOperacional emTratamento() {
        ItemFilaOperacional item = novoAberto();
        item.assumir(UUID.randomUUID());
        return item;
    }
}
