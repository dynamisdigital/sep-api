package com.dynamis.sep_api.backoffice.application.service;

import com.dynamis.sep_api.backoffice.application.service.CriarItemFilaOperacionalService.CriarItemCommand;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

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
    private ItemFilaOperacionalInserter inserter;
    private CriarItemFilaOperacionalService service;

    @BeforeEach
    void setup() {
        repository = mock(ItemFilaOperacionalRepository.class);
        inserter = mock(ItemFilaOperacionalInserter.class);
        service = new CriarItemFilaOperacionalService(repository, inserter);
    }

    @Test
    void naoCriaQuandoJaExisteAtivo() {
        when(repository.existsByTipoAndTipoEntidadeAndEntidadeIdAndStatusIn(any(), any(), any(), any()))
                .thenReturn(true);

        Optional<UUID> resultado = service.criarSeAusente(command());

        assertThat(resultado).isEmpty();
        verify(inserter, never()).inserir(any());
    }

    @Test
    void criaQuandoAusente_delegaParaInserter() {
        UUID gerado = UUID.randomUUID();
        when(repository.existsByTipoAndTipoEntidadeAndEntidadeIdAndStatusIn(any(), any(), any(), any()))
                .thenReturn(false);
        when(inserter.inserir(any())).thenReturn(gerado);

        Optional<UUID> resultado = service.criarSeAusente(command());

        assertThat(resultado).contains(gerado);
        verify(inserter, times(1)).inserir(any());
    }

    @Test
    void raceNoInsert_capturaDataIntegrityForaDaTxEDevolveEmpty() {
        when(repository.existsByTipoAndTipoEntidadeAndEntidadeIdAndStatusIn(any(), any(), any(), any()))
                .thenReturn(false);
        when(inserter.inserir(any())).thenThrow(new DataIntegrityViolationException("uq_item_ativo_por_entidade"));

        Optional<UUID> resultado = service.criarSeAusente(command());

        assertThat(resultado).isEmpty();
    }

    @Test
    void existsConsultaAtivos() {
        when(repository.existsByTipoAndTipoEntidadeAndEntidadeIdAndStatusIn(any(), any(), any(), any()))
                .thenReturn(false);
        when(inserter.inserir(any())).thenReturn(UUID.randomUUID());

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
