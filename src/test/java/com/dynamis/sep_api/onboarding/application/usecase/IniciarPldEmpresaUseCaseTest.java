package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.application.port.out.BackgroundCheckProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.HitPld;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaPld;
import com.dynamis.sep_api.onboarding.domain.model.ConsultaPld;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.RepresentanteLegal;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.BasePld;
import com.dynamis.sep_api.onboarding.domain.vo.Cnpj;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.SeveridadePld;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.StatusPldRepresentante;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSocietario;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaPldRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.KybEmpresaRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.RepresentanteLegalRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IniciarPldEmpresaUseCaseTest {

    private static final String CNPJ = "11222333000181";

    private SolicitacaoOnboardingRepository solicitacaoRepository;
    private KybEmpresaRepository kybRepository;
    private RepresentanteLegalRepository representanteRepository;
    private ConsultaPldRepository consultaPldRepository;
    private BackgroundCheckProvider provider;
    private ApplicationEventPublisher eventPublisher;
    private IniciarPldEmpresaUseCase useCase;
    private SolicitacaoOnboarding pjAprovada;
    private KybEmpresa kyb;
    private RepresentanteLegal rep1;

    @BeforeEach
    void setup() {
        solicitacaoRepository = mock(SolicitacaoOnboardingRepository.class);
        kybRepository = mock(KybEmpresaRepository.class);
        representanteRepository = mock(RepresentanteLegalRepository.class);
        consultaPldRepository = mock(ConsultaPldRepository.class);
        provider = mock(BackgroundCheckProvider.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new IniciarPldEmpresaUseCase(
                solicitacaoRepository,
                kybRepository,
                representanteRepository,
                consultaPldRepository,
                provider,
                eventPublisher);

        pjAprovada = SolicitacaoOnboarding.criarEmpresa(UUID.randomUUID(), CNPJ, "ACME LTDA");
        pjAprovada.registrarDocumentoEnviado();
        pjAprovada.marcarEmVerificacao("ext");
        pjAprovada.finalizar(StatusOnboarding.APROVADO);

        kyb = KybEmpresa.criar(
                pjAprovada.getId(), new Cnpj(CNPJ), "ACME LTDA", null, TipoSocietario.LTDA, PorteEmpresa.MEDIO);
        rep1 = RepresentanteLegal.criar(kyb.getId(), "Joao Silva", new Cpf("52998224725"), "CEO");

        when(solicitacaoRepository.findById(pjAprovada.getId())).thenReturn(Optional.of(pjAprovada));
        when(kybRepository.findBySolicitacaoId(pjAprovada.getId())).thenReturn(Optional.of(kyb));
        when(representanteRepository.findByKybEmpresaId(kyb.getId())).thenReturn(List.of(rep1));
        when(solicitacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(representanteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void empresaLimpaERepresentanteLimpoMovePraAprovadoFinal() {
        when(provider.consultarEmpresa(any(), anyString())).thenReturn(new RespostaPld(List.of(), "{}"));
        when(provider.consultarPessoa(any(), anyString())).thenReturn(new RespostaPld(List.of(), "{}"));

        StatusOnboarding s = useCase.executar(pjAprovada.getId(), "corr-1");

        assertThat(s).isEqualTo(StatusOnboarding.APROVADO_FINAL);
        // 4 bases limpas pra empresa + 4 pra representante = 8 ConsultaPld
        verify(consultaPldRepository, times(8)).save(any(ConsultaPld.class));
        assertThat(rep1.getStatusPld()).isEqualTo(StatusPldRepresentante.LIMPO);
    }

    @Test
    void hitNaEmpresaReprovaPorPld() {
        when(provider.consultarEmpresa(any(), anyString()))
                .thenReturn(new RespostaPld(
                        List.of(new HitPld(BasePld.COAF, "Sancao", SeveridadePld.ALTA, LocalDate.now(), "{}")), "{}"));
        when(provider.consultarPessoa(any(), anyString())).thenReturn(new RespostaPld(List.of(), "{}"));

        StatusOnboarding s = useCase.executar(pjAprovada.getId(), "corr-2");

        assertThat(s).isEqualTo(StatusOnboarding.REPROVADO_PLD);
    }

    @Test
    void hitNoRepresentanteReprovaPorPld() {
        when(provider.consultarEmpresa(any(), anyString())).thenReturn(new RespostaPld(List.of(), "{}"));
        when(provider.consultarPessoa(any(), anyString()))
                .thenReturn(new RespostaPld(
                        List.of(new HitPld(BasePld.OFAC, "Sancao internacional", SeveridadePld.MEDIA, null, "{}")),
                        "{}"));

        StatusOnboarding s = useCase.executar(pjAprovada.getId(), "corr-3");

        assertThat(s).isEqualTo(StatusOnboarding.REPROVADO_PLD);
        assertThat(rep1.getStatusPld()).isEqualTo(StatusPldRepresentante.HIT);
    }

    @Test
    void reexecucaoEmStatusJaFinalizadoNaoDisparaProvider() {
        SolicitacaoOnboarding ja = SolicitacaoOnboarding.criarEmpresa(UUID.randomUUID(), CNPJ, "ACME LTDA");
        ja.registrarDocumentoEnviado();
        ja.marcarEmVerificacao("ext");
        ja.finalizar(StatusOnboarding.APROVADO);
        ja.reprovarPorPld();
        when(solicitacaoRepository.findById(ja.getId())).thenReturn(Optional.of(ja));

        StatusOnboarding s = useCase.executar(ja.getId(), "corr-reexec");

        assertThat(s).isEqualTo(StatusOnboarding.REPROVADO_PLD);
        verify(provider, org.mockito.Mockito.never()).consultarEmpresa(any(), anyString());
        verify(provider, org.mockito.Mockito.never()).consultarPessoa(any(), anyString());
    }
}
