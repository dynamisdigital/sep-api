package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.domain.event.OnboardingIniciadoEvent;
import com.dynamis.sep_api.onboarding.domain.exception.CpfComOnboardingAtivoException;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IniciarOnboardingPessoaUseCaseTest {

    private SolicitacaoOnboardingRepository repository;
    private ApplicationEventPublisher eventPublisher;
    private IniciarOnboardingPessoaUseCase useCase;

    @BeforeEach
    void setup() {
        repository = mock(SolicitacaoOnboardingRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new IniciarOnboardingPessoaUseCase(repository, eventPublisher);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void criaSolicitacaoEPublicaEventoOnboardingIniciado() {
        when(repository.existsByCpfAndStatusIn(anyString(), any(Collection.class)))
                .thenReturn(false);
        UUID usuarioId = UUID.randomUUID();

        SolicitacaoOnboarding s = useCase.executar(usuarioId, "52998224725", "Joao", LocalDate.of(1990, 1, 1));

        assertThat(s.getStatus()).isEqualTo(StatusOnboarding.INICIADO);
        assertThat(s.getCpf()).isEqualTo("52998224725");

        ArgumentCaptor<OnboardingIniciadoEvent> captor = ArgumentCaptor.forClass(OnboardingIniciadoEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().usuarioId()).isEqualTo(usuarioId);
    }

    @Test
    void rejeitaCpfDuplicadoEmStatusAtivo() {
        when(repository.existsByCpfAndStatusIn(anyString(), any(Collection.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID(), "52998224725", "Joao", LocalDate.of(1990, 1, 1)))
                .isInstanceOf(CpfComOnboardingAtivoException.class);
    }

    @Test
    void rejeitaCpfInvalidoComoValidacaoException() {
        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID(), "11111111111", "Joao", LocalDate.of(1990, 1, 1)))
                .isInstanceOf(ValidacaoException.class);
    }

    @Test
    void rejeitaCpfNuloComoValidacaoException() {
        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID(), null, "Joao", LocalDate.of(1990, 1, 1)))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("obrigatorio");
    }

    @Test
    void rejeitaCpfEmBrancoComoValidacaoException() {
        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID(), "   ", "Joao", LocalDate.of(1990, 1, 1)))
                .isInstanceOf(ValidacaoException.class)
                .hasMessageContaining("obrigatorio");
    }
}
