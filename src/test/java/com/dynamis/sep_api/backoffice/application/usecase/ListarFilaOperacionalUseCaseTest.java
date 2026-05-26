package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.application.dto.FiltrosFilaOperacional;
import com.dynamis.sep_api.backoffice.application.dto.ItemFilaSummary;
import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListarFilaOperacionalUseCaseTest {

    private ItemFilaOperacionalRepository repository;
    private ListarFilaOperacionalUseCase useCase;

    @BeforeEach
    void setup() {
        repository = mock(ItemFilaOperacionalRepository.class);
        useCase = new ListarFilaOperacionalUseCase(repository);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item())));
    }

    @Test
    void mapeiaParaSummary() {
        Page<ItemFilaSummary> page = useCase.listar(FiltrosFilaOperacional.vazio(), PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).status()).isEqualTo(StatusItemFila.ABERTO);
    }

    @Test
    void pageSizeAcima100_eClampadoPara100() {
        useCase.listar(FiltrosFilaOperacional.vazio(), PageRequest.of(0, 500));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(ListarFilaOperacionalUseCase.PAGE_SIZE_MAX);
    }

    @Test
    void pageableUnpaged_normalizaParaPageSizeMax() {
        useCase.listar(FiltrosFilaOperacional.vazio(), Pageable.unpaged());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(ListarFilaOperacionalUseCase.PAGE_SIZE_MAX);
        assertThat(captor.getValue().getPageNumber()).isZero();
    }

    @Test
    void pageableNull_normalizaParaPrimeiraPagina100() {
        useCase.listar(FiltrosFilaOperacional.vazio(), null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(ListarFilaOperacionalUseCase.PAGE_SIZE_MAX);
    }

    @Test
    void sortDoCallerEhPreservado() {
        Sort sortCaller = Sort.by(Sort.Direction.DESC, "dataAbertura");
        useCase.listar(FiltrosFilaOperacional.vazio(), PageRequest.of(0, 10, sortCaller));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(sortCaller);
    }

    @Test
    void filtrosNull_naoQuebra() {
        Page<ItemFilaSummary> page = useCase.listar(null, PageRequest.of(0, 10));
        assertThat(page).isNotNull();
    }

    @Test
    void filtrosCombinados_naoLancam() {
        FiltrosFilaOperacional f = new FiltrosFilaOperacional(
                TipoItemFila.COBRANCA_INADIMPLENTE,
                PrioridadeItem.ALTA,
                StatusItemFila.ABERTO,
                OffsetDateTime.parse("2026-05-01T00:00:00Z"),
                OffsetDateTime.parse("2026-05-26T23:59:59Z"),
                UUID.randomUUID());

        useCase.listar(f, PageRequest.of(0, 10));

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    private ItemFilaOperacional item() {
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
