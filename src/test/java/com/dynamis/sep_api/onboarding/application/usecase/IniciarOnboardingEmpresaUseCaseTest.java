package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.domain.event.KybIniciadoEvent;
import com.dynamis.sep_api.onboarding.domain.exception.CnpjComOnboardingAtivoException;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSocietario;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IniciarOnboardingEmpresaUseCaseTest {

    private static final String CNPJ_VALIDO = "11222333000181";

    private SolicitacaoOnboardingRepository solicitacaoRepository;
    private KybEmpresaRepository kybRepository;
    private ApplicationEventPublisher eventPublisher;
    private IniciarOnboardingEmpresaUseCase useCase;

    @BeforeEach
    void setup() {
        solicitacaoRepository = mock(SolicitacaoOnboardingRepository.class);
        kybRepository = mock(KybEmpresaRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new IniciarOnboardingEmpresaUseCase(solicitacaoRepository, kybRepository, eventPublisher);
        when(solicitacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(kybRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void criaSolicitacaoEKybEPublicaKybIniciadoEvent() {
        when(solicitacaoRepository.existsByDocumentoAndStatusIn(anyString(), any(Collection.class)))
                .thenReturn(false);
        UUID usuarioId = UUID.randomUUID();

        SolicitacaoOnboarding s =
                useCase.executar(usuarioId, CNPJ_VALIDO, "ACME LTDA", "ACME", TipoSocietario.LTDA, PorteEmpresa.MEDIO);

        assertThat(s.getStatus()).isEqualTo(StatusOnboarding.INICIADO);
        assertThat(s.getDocumento()).isEqualTo(CNPJ_VALIDO);

        ArgumentCaptor<KybEmpresa> kybCaptor = ArgumentCaptor.forClass(KybEmpresa.class);
        verify(kybRepository).save(kybCaptor.capture());
        assertThat(kybCaptor.getValue().getCnpj()).isEqualTo(CNPJ_VALIDO);
        assertThat(kybCaptor.getValue().getTipoSocietario()).isEqualTo(TipoSocietario.LTDA);

        ArgumentCaptor<KybIniciadoEvent> evtCaptor = ArgumentCaptor.forClass(KybIniciadoEvent.class);
        verify(eventPublisher).publishEvent(evtCaptor.capture());
        assertThat(evtCaptor.getValue().usuarioId()).isEqualTo(usuarioId);
        assertThat(evtCaptor.getValue().cnpj()).isEqualTo(CNPJ_VALIDO);
    }

    @Test
    void rejeitaCnpjDuplicadoEmStatusAtivo() {
        when(solicitacaoRepository.existsByDocumentoAndStatusIn(anyString(), any(Collection.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(
                        UUID.randomUUID(), CNPJ_VALIDO, "ACME", null, TipoSocietario.LTDA, PorteEmpresa.ME))
                .isInstanceOf(CnpjComOnboardingAtivoException.class);
    }

    @Test
    void rejeitaCnpjInvalido() {
        assertThatThrownBy(() -> useCase.executar(
                        UUID.randomUUID(), "11111111111111", "ACME", null, TipoSocietario.LTDA, PorteEmpresa.ME))
                .isInstanceOf(ValidacaoException.class);
    }

    @Test
    void rejeitaRazaoSocialAusente() {
        when(solicitacaoRepository.existsByDocumentoAndStatusIn(anyString(), any(Collection.class)))
                .thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(
                        UUID.randomUUID(), CNPJ_VALIDO, " ", null, TipoSocietario.LTDA, PorteEmpresa.ME))
                .isInstanceOf(ValidacaoException.class);
    }
}
