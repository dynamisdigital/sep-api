package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.domain.exception.OnboardingNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.RepresentanteLegal;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cnpj;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSocietario;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.RepresentanteLegalRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsultarRepresentantesLegaisUseCaseTest {

    private static final String CNPJ = "11222333000181";

    private SolicitacaoOnboardingRepository solicitacaoRepository;
    private KybEmpresaRepository kybRepository;
    private RepresentanteLegalRepository representanteRepository;
    private ConsultarRepresentantesLegaisUseCase useCase;
    private UUID ownerId;
    private SolicitacaoOnboarding sol;
    private KybEmpresa kyb;
    private RepresentanteLegal rep;

    @BeforeEach
    void setup() {
        solicitacaoRepository = mock(SolicitacaoOnboardingRepository.class);
        kybRepository = mock(KybEmpresaRepository.class);
        representanteRepository = mock(RepresentanteLegalRepository.class);
        useCase =
                new ConsultarRepresentantesLegaisUseCase(solicitacaoRepository, kybRepository, representanteRepository);

        ownerId = UUID.randomUUID();
        sol = SolicitacaoOnboarding.criarEmpresa(ownerId, CNPJ, "ACME");
        kyb = KybEmpresa.criar(sol.getId(), new Cnpj(CNPJ), "ACME", null, TipoSocietario.LTDA, PorteEmpresa.MEDIO);
        rep = RepresentanteLegal.criar(kyb.getId(), "Joao", new Cpf("52998224725"), "CEO");

        when(solicitacaoRepository.findById(sol.getId())).thenReturn(Optional.of(sol));
        when(kybRepository.findBySolicitacaoId(sol.getId())).thenReturn(Optional.of(kyb));
        when(representanteRepository.findByKybEmpresaId(kyb.getId())).thenReturn(List.of(rep));
    }

    @Test
    void ownerListaRepresentantes() {
        List<RepresentanteLegal> result = useCase.executar(sol.getId(), ownerId, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCpf()).isEqualTo("52998224725");
    }

    @Test
    void adminListaRepresentantesDeOutroOwner() {
        List<RepresentanteLegal> result = useCase.executar(sol.getId(), UUID.randomUUID(), true);

        assertThat(result).hasSize(1);
    }

    @Test
    void usuarioAlheioRecebeAccessDenied() {
        assertThatThrownBy(() -> useCase.executar(sol.getId(), UUID.randomUUID(), false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void solicitacaoInexistenteLancaNaoEncontrado() {
        UUID idDesconhecido = UUID.randomUUID();
        when(solicitacaoRepository.findById(idDesconhecido)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(idDesconhecido, ownerId, false))
                .isInstanceOf(OnboardingNaoEncontradoException.class);
    }
}
