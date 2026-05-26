package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.domain.event.ComentarioRegistradoEvent;
import com.dynamis.sep_api.backoffice.domain.exception.ItemFilaNaoEncontradoException;
import com.dynamis.sep_api.backoffice.domain.model.ComentarioInterno;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ComentarioInternoRepository;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrarComentarioUseCaseTest {

    private ItemFilaOperacionalRepository itemRepo;
    private ComentarioInternoRepository comentarioRepo;
    private ApplicationEventPublisher publisher;
    private RegistrarComentarioUseCase useCase;

    @BeforeEach
    void setup() {
        itemRepo = mock(ItemFilaOperacionalRepository.class);
        comentarioRepo = mock(ComentarioInternoRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        useCase = new RegistrarComentarioUseCase(itemRepo, comentarioRepo, publisher);
        when(comentarioRepo.save(any(ComentarioInterno.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void happy_persisteEPublica() {
        UUID itemId = UUID.randomUUID();
        when(itemRepo.existsById(itemId)).thenReturn(true);

        ComentarioInterno salvo = useCase.executar(itemId, UUID.randomUUID(), "obs curta");

        assertThat(salvo.getItemId()).isEqualTo(itemId);
        verify(publisher).publishEvent(any(ComentarioRegistradoEvent.class));
    }

    @Test
    void itemAusente_lanca404() {
        UUID itemId = UUID.randomUUID();
        when(itemRepo.existsById(itemId)).thenReturn(false);

        assertThatExceptionOfType(ItemFilaNaoEncontradoException.class)
                .isThrownBy(() -> useCase.executar(itemId, UUID.randomUUID(), "obs"));
    }

    @Test
    void conteudoVazio_lancaIllegalArgument() {
        UUID itemId = UUID.randomUUID();
        when(itemRepo.existsById(itemId)).thenReturn(true);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> useCase.executar(itemId, UUID.randomUUID(), "  "));
    }

    @Test
    void resumoTruncadoEm80Caracteres() {
        UUID itemId = UUID.randomUUID();
        when(itemRepo.existsById(itemId)).thenReturn(true);
        String longo = "x".repeat(120);

        useCase.executar(itemId, UUID.randomUUID(), longo);

        ArgumentCaptor<ComentarioRegistradoEvent> captor = ArgumentCaptor.forClass(ComentarioRegistradoEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().conteudoResumido()).endsWith("...").hasSize(83);
    }
}
