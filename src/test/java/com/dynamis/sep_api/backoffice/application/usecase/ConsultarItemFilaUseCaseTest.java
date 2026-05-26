package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.application.dto.ItemFilaDetalhe;
import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.application.service.ResolvedorObjetoOriginalDispatcher;
import com.dynamis.sep_api.backoffice.domain.exception.ItemFilaNaoEncontradoException;
import com.dynamis.sep_api.backoffice.domain.model.ComentarioInterno;
import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ComentarioInternoRepository;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsultarItemFilaUseCaseTest {

    private ItemFilaOperacionalRepository itemRepo;
    private ComentarioInternoRepository comentarioRepo;
    private ResolvedorObjetoOriginalDispatcher resolvedor;
    private ConsultarItemFilaUseCase useCase;

    @BeforeEach
    void setup() {
        itemRepo = mock(ItemFilaOperacionalRepository.class);
        comentarioRepo = mock(ComentarioInternoRepository.class);
        resolvedor = mock(ResolvedorObjetoOriginalDispatcher.class);
        useCase = new ConsultarItemFilaUseCase(itemRepo, comentarioRepo, resolvedor);
    }

    @Test
    void naoEncontrado_lanca404() {
        UUID id = UUID.randomUUID();
        when(itemRepo.findById(id)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ItemFilaNaoEncontradoException.class).isThrownBy(() -> useCase.consultar(id));
    }

    @Test
    void semStrategy_devolveDetalheComObjetoOriginalNull() {
        ItemFilaOperacional item = item();
        when(itemRepo.findById(item.getId())).thenReturn(Optional.of(item));
        when(comentarioRepo.findByItemIdOrderByDataCriacaoAsc(item.getId())).thenReturn(List.of());
        when(resolvedor.resolver(any(), any())).thenReturn(Optional.empty());

        ItemFilaDetalhe detalhe = useCase.consultar(item.getId());

        assertThat(detalhe.id()).isEqualTo(item.getId());
        assertThat(detalhe.objetoOriginal()).isNull();
        assertThat(detalhe.comentarios()).isEmpty();
    }

    @Test
    void carregaComentariosEObjetoOriginal() {
        ItemFilaOperacional item = item();
        ComentarioInterno c = ComentarioInterno.registrar(item.getId(), UUID.randomUUID(), "obs");
        ObjetoOriginalResumo resumo =
                new ObjetoOriginalResumo(TipoEntidadeReferenciada.ONBOARDING, item.getEntidadeId(), "REPROVADO", "...");
        when(itemRepo.findById(item.getId())).thenReturn(Optional.of(item));
        when(comentarioRepo.findByItemIdOrderByDataCriacaoAsc(item.getId())).thenReturn(List.of(c));
        when(resolvedor.resolver(any(), any())).thenReturn(Optional.of(resumo));

        ItemFilaDetalhe detalhe = useCase.consultar(item.getId());

        assertThat(detalhe.comentarios()).hasSize(1);
        assertThat(detalhe.objetoOriginal()).isEqualTo(resumo);
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
