package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.domain.event.ItemIgnoradoEvent;
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

class MarcarItemIgnoradoUseCaseTest {

    private ItemFilaOperacionalRepository itemRepo;
    private ComentarioInternoRepository comentarioRepo;
    private ApplicationEventPublisher publisher;
    private MarcarItemIgnoradoUseCase useCase;

    @BeforeEach
    void setup() {
        itemRepo = mock(ItemFilaOperacionalRepository.class);
        comentarioRepo = mock(ComentarioInternoRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);
        useCase = new MarcarItemIgnoradoUseCase(itemRepo, comentarioRepo, publisher, clock);
        when(itemRepo.save(any(ItemFilaOperacional.class))).thenAnswer(inv -> inv.getArgument(0));
        when(comentarioRepo.save(any(ComentarioInterno.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void ignoraAPartirDeAberto() {
        ItemFilaOperacional item = novoAberto();
        when(itemRepo.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));

        ItemFilaOperacional resultado = useCase.executar(
                item.getId(), UUID.randomUUID(), "Onboarding ja resolvido manualmente fora do sistema");

        assertThat(resultado.getStatus()).isEqualTo(StatusItemFila.IGNORADO);
        verify(publisher).publishEvent(any(ItemIgnoradoEvent.class));
    }

    @Test
    void ignoraAPartirDeEmTratamento() {
        ItemFilaOperacional item = novoAberto();
        item.assumir(UUID.randomUUID());
        when(itemRepo.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));

        ItemFilaOperacional resultado = useCase.executar(
                item.getId(), UUID.randomUUID(), "Justificativa adequada com mais de vinte caracteres");

        assertThat(resultado.getStatus()).isEqualTo(StatusItemFila.IGNORADO);
    }

    @Test
    void itemFinal_lanca409() {
        ItemFilaOperacional item = novoAberto();
        item.assumir(UUID.randomUUID());
        item.resolver(OffsetDateTime.now());
        when(itemRepo.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));

        assertThatExceptionOfType(TransicaoItemInvalidaException.class)
                .isThrownBy(() -> useCase.executar(item.getId(), UUID.randomUUID(), "Justificativa com tamanho minimo aceitavel"));
    }

    @Test
    void justificativaCurta_lanca400() {
        assertThatExceptionOfType(JustificativaInvalidaException.class)
                .isThrownBy(() -> useCase.executar(UUID.randomUUID(), UUID.randomUUID(), "curta"));
    }

    private ItemFilaOperacional novoAberto() {
        return ItemFilaOperacional.abrir(
                TipoItemFila.OUTRO,
                PrioridadeItem.BAIXA,
                TipoEntidadeReferenciada.OUTRO,
                UUID.randomUUID(),
                "Item generico",
                null,
                OffsetDateTime.now());
    }
}
