package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.application.port.out.BackgroundCheckProvider;
import com.dynamis.sep_api.onboarding.application.port.out.dto.HitPld;
import com.dynamis.sep_api.onboarding.application.port.out.dto.RespostaPld;
import com.dynamis.sep_api.onboarding.domain.model.ConsultaPld;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.BasePld;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.SeveridadePld;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.ConsultaPldRepository;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IniciarPldPessoaUseCaseTest {

    private SolicitacaoOnboardingRepository solicitacaoRepository;
    private ConsultaPldRepository consultaPldRepository;
    private BackgroundCheckProvider provider;
    private ApplicationEventPublisher eventPublisher;
    private IniciarPldPessoaUseCase useCase;
    private SolicitacaoOnboarding pfAprovada;

    @BeforeEach
    void setup() {
        solicitacaoRepository = mock(SolicitacaoOnboardingRepository.class);
        consultaPldRepository = mock(ConsultaPldRepository.class);
        provider = mock(BackgroundCheckProvider.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new IniciarPldPessoaUseCase(solicitacaoRepository, consultaPldRepository, provider, eventPublisher);

        pfAprovada = SolicitacaoOnboarding.criarPessoa(
                UUID.randomUUID(), new Cpf("52998224725"), "Joao", LocalDate.of(1990, 1, 1));
        pfAprovada.registrarDocumentoEnviado();
        pfAprovada.marcarEmVerificacao("ext-001");
        pfAprovada.finalizar(StatusOnboarding.APROVADO);

        when(solicitacaoRepository.findById(pfAprovada.getId())).thenReturn(Optional.of(pfAprovada));
        when(solicitacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void pldLimpoEm4BasesMovePraAprovadoFinal() {
        when(provider.consultarPessoa(any(), anyString())).thenReturn(new RespostaPld(List.of(), "{}"));

        StatusOnboarding s = useCase.executar(pfAprovada.getId(), "corr-1");

        assertThat(s).isEqualTo(StatusOnboarding.APROVADO_FINAL);
        assertThat(pfAprovada.getStatus()).isEqualTo(StatusOnboarding.APROVADO_FINAL);
        // 4 registros limpos (1 por base obrigatoria)
        verify(consultaPldRepository, times(4)).save(any(ConsultaPld.class));
    }

    @Test
    void hitEmQualquerBaseReprovaPorPld() {
        when(provider.consultarPessoa(any(), anyString()))
                .thenReturn(new RespostaPld(
                        List.of(new HitPld(BasePld.OFAC, "Sancao", SeveridadePld.ALTA, LocalDate.now(), "{}")), "{}"));

        StatusOnboarding s = useCase.executar(pfAprovada.getId(), "corr-2");

        assertThat(s).isEqualTo(StatusOnboarding.REPROVADO_PLD);
        assertThat(pfAprovada.getStatus()).isEqualTo(StatusOnboarding.REPROVADO_PLD);
        // 1 hit (OFAC) + 3 limpas (COAF/INTERPOL/MTE)
        verify(consultaPldRepository, times(4)).save(any(ConsultaPld.class));
    }

    @Test
    void reexecucaoEmStatusJaFinalizadoNaoDisparaProvider() {
        SolicitacaoOnboarding ja = SolicitacaoOnboarding.criarPessoa(
                UUID.randomUUID(), new Cpf("52998224725"), "Joao", LocalDate.of(1990, 1, 1));
        ja.registrarDocumentoEnviado();
        ja.marcarEmVerificacao("ext");
        ja.finalizar(StatusOnboarding.APROVADO);
        ja.marcarAprovadoFinal();
        when(solicitacaoRepository.findById(ja.getId())).thenReturn(Optional.of(ja));

        StatusOnboarding s = useCase.executar(ja.getId(), "corr-reexec");

        assertThat(s).isEqualTo(StatusOnboarding.APROVADO_FINAL);
        verify(provider, org.mockito.Mockito.never()).consultarPessoa(any(), anyString());
        verify(consultaPldRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void rejeitaSolicitacaoEmpresa() {
        SolicitacaoOnboarding pj = SolicitacaoOnboarding.criarEmpresa(UUID.randomUUID(), "11222333000181", "ACME");
        when(solicitacaoRepository.findById(pj.getId())).thenReturn(Optional.of(pj));

        assertThatThrownBy(() -> useCase.executar(pj.getId(), "corr-3")).isInstanceOf(ValidacaoException.class);
    }

    @Test
    void rejeitaSolicitacaoNaoAprovada() {
        SolicitacaoOnboarding pfIniciada = SolicitacaoOnboarding.criarPessoa(
                UUID.randomUUID(), new Cpf("52998224725"), "Joao", LocalDate.of(1990, 1, 1));
        when(solicitacaoRepository.findById(pfIniciada.getId())).thenReturn(Optional.of(pfIniciada));

        assertThatThrownBy(() -> useCase.executar(pfIniciada.getId(), "corr-4")).isInstanceOf(ValidacaoException.class);
    }
}
