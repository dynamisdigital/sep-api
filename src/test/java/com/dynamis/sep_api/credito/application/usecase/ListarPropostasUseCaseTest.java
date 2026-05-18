package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ListarPropostasUseCaseTest {

    private PropostaCreditoRepository repo;
    private ListarPropostasUseCase useCase;

    @BeforeEach
    void setup() {
        repo = mock(PropostaCreditoRepository.class);
        useCase = new ListarPropostasUseCase(repo);
    }

    @Test
    void tomadorSemStatusUsaFindByTomadorId() {
        UUID tomador = UUID.randomUUID();
        Pageable pg = PageRequest.of(0, 10);
        Page<PropostaCredito> empty = new PageImpl<>(List.of());
        when(repo.findByTomadorId(tomador, pg)).thenReturn(empty);

        useCase.listarDoTomador(tomador, null, pg);

        verify(repo).findByTomadorId(tomador, pg);
        verifyNoMoreInteractions(repo);
    }

    @Test
    void tomadorComStatusUsaFindByStatusInAndTomadorId() {
        UUID tomador = UUID.randomUUID();
        Pageable pg = PageRequest.of(0, 10);
        Page<PropostaCredito> empty = new PageImpl<>(List.of());
        when(repo.findByStatusInAndTomadorId(any(Collection.class), any(UUID.class), any(Pageable.class)))
                .thenReturn(empty);

        useCase.listarDoTomador(tomador, StatusProposta.EM_ANALISE, pg);

        verify(repo).findByStatusInAndTomadorId(any(Collection.class), any(UUID.class), any(Pageable.class));
    }

    @Test
    void financeiroFiltrosNullRetornaFindAll() {
        Pageable pg = PageRequest.of(0, 10);
        when(repo.findAll(pg)).thenReturn(new PageImpl<>(List.of()));

        useCase.listarComFiltros(null, null, pg);
        verify(repo).findAll(pg);
    }

    @Test
    void financeiroComStatusUsaFindByStatus() {
        Pageable pg = PageRequest.of(0, 10);
        when(repo.findByStatus(StatusProposta.PRE_APROVADA, pg)).thenReturn(new PageImpl<>(List.of()));

        useCase.listarComFiltros(null, StatusProposta.PRE_APROVADA, pg);
        verify(repo).findByStatus(StatusProposta.PRE_APROVADA, pg);
    }
}
