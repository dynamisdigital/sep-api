package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.domain.exception.SolicitacaoNaoEmpresaException;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.DocumentoCadastralRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.RepresentanteLegalRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ResultadoVerificacaoRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConsultarStatusOnboardingEmpresaUseCaseTest {

    /** O ramo PF deste use case nao tinha teste; o codigo e o literal publicado, nao a constante. */
    @Test
    void rejeitaSolicitacaoPFAntesDeConsultarOKyb() {
        SolicitacaoOnboardingRepository solicitacaoRepository = mock(SolicitacaoOnboardingRepository.class);
        KybEmpresaRepository kybRepository = mock(KybEmpresaRepository.class);
        ConsultarStatusOnboardingEmpresaUseCase useCase = new ConsultarStatusOnboardingEmpresaUseCase(
                solicitacaoRepository,
                kybRepository,
                mock(DocumentoCadastralRepository.class),
                mock(RepresentanteLegalRepository.class),
                mock(ResultadoVerificacaoRepository.class));
        UUID usuarioId = UUID.randomUUID();
        SolicitacaoOnboarding pf =
                SolicitacaoOnboarding.criarPessoa(usuarioId, new Cpf("52998224725"), "Joao", LocalDate.of(1990, 1, 1));
        when(solicitacaoRepository.findById(pf.getId())).thenReturn(Optional.of(pf));

        assertThatThrownBy(() -> useCase.executar(pf.getId(), usuarioId, false))
                .isInstanceOf(SolicitacaoNaoEmpresaException.class)
                .hasFieldOrPropertyWithValue("codigo", "ONB-400-008")
                .hasMessage("Solicitacao nao e do tipo EMPRESA");
        verifyNoInteractions(kybRepository);
    }
}
