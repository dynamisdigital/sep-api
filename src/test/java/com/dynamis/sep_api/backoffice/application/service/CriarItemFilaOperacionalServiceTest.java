package com.dynamis.sep_api.backoffice.application.service;

import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.event.ItemFilaCriadoEvent;
import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CriarItemFilaOperacionalServiceTest {

    private ItemFilaOperacionalRepository repository;
    private ApplicationEventPublisher publisher;
    private Clock clock;
    private CriarItemFilaOperacionalService service;

    @BeforeEach
    void setup() {
        repository = mock(ItemFilaOperacionalRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        clock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);
        service = new CriarItemFilaOperacionalService(repository, publisher, clock);
    }

    @Test
    void naoCriaQuandoJaExisteAtivo() {
        when(repository.existsByTipoAndTipoEntidadeAndEntidadeIdAndStatusIn(any(), any(), any(), any()))
                .thenReturn(true);

        Optional<UUID> resultado = service.criarSeAusente(command());

        assertThat(resultado).isEmpty();
        verify(repository, never()).saveAndFlush(any(ItemFilaOperacional.class));
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void criaQuandoAusenteEPublicaEvento() {
        when(repository.existsByTipoAndTipoEntidadeAndEntidadeIdAndStatusIn(any(), any(), any(), any()))
                .thenReturn(false);
        when(repository.saveAndFlush(any(ItemFilaOperacional.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<UUID> resultado = service.criarSeAusente(command());

        assertThat(resultado).isPresent();
        verify(publisher, times(1)).publishEvent(any(ItemFilaCriadoEvent.class));
    }

    @Test
    void raceNoInsert_capturaDataIntegrityEDevolveEmpty() {
        when(repository.existsByTipoAndTipoEntidadeAndEntidadeIdAndStatusIn(any(), any(), any(), any()))
                .thenReturn(false);
        when(repository.saveAndFlush(any(ItemFilaOperacional.class)))
                .thenThrow(new DataIntegrityViolationException("uq_item_ativo_por_entidade"));

        Optional<UUID> resultado = service.criarSeAusente(command());

        assertThat(resultado).isEmpty();
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void existsConsultaAteAmbosStatusAtivos() {
        when(repository.existsByTipoAndTipoEntidadeAndEntidadeIdAndStatusIn(any(), any(), any(), any()))
                .thenReturn(false);
        when(repository.saveAndFlush(any(ItemFilaOperacional.class))).thenAnswer(inv -> inv.getArgument(0));

        service.criarSeAusente(command());

        verify(repository)
                .existsByTipoAndTipoEntidadeAndEntidadeIdAndStatusIn(
                        eq(TipoItemFila.ONBOARDING_ERRO),
                        eq(TipoEntidadeReferenciada.ONBOARDING),
                        any(UUID.class),
                        argThat(set -> set.contains(StatusItemFila.ABERTO)
                                && set.contains(StatusItemFila.EM_TRATAMENTO)));
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }

    private CriarItemCommand command() {
        return new CriarItemCommand(
                TipoItemFila.ONBOARDING_ERRO,
                PrioridadeItem.ALTA,
                TipoEntidadeReferenciada.ONBOARDING,
                UUID.randomUUID(),
                "Onboarding REPROVADO",
                null);
    }
}
