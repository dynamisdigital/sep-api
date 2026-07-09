package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.AporteCredoraView;
import com.dynamis.sep_api.credores.domain.exception.AporteOperacaoNaoEncontradaException;
import com.dynamis.sep_api.credores.domain.model.AporteCredora;
import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import com.dynamis.sep_api.credores.domain.model.OperacaoFinanciada;
import com.dynamis.sep_api.credores.domain.vo.StatusAporteCredora;
import com.dynamis.sep_api.credores.infrastructure.persistence.AporteCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OperacaoFinanciadaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes da consulta owner-scoped de aportes (Sprint 29 Task 29.6): visao operacional, credora
 * dona, 404 neutro indistinguivel e lista vazia valida.
 */
class ConsultarAportesOperacaoUseCaseTest {

    private OperacaoFinanciadaRepository operacaoRepository;
    private EmpresaCredoraRepository empresaRepository;
    private AporteCredoraRepository aporteRepository;
    private ConsultarAportesOperacaoUseCase useCase;

    private final UUID usuarioId = UUID.randomUUID();
    private OperacaoFinanciada operacao;
    private EmpresaCredora credora;

    @BeforeEach
    void setup() {
        operacaoRepository = mock(OperacaoFinanciadaRepository.class);
        empresaRepository = mock(EmpresaCredoraRepository.class);
        aporteRepository = mock(AporteCredoraRepository.class);
        useCase = new ConsultarAportesOperacaoUseCase(operacaoRepository, empresaRepository, aporteRepository);

        credora = mock(EmpresaCredora.class);
        when(credora.getId()).thenReturn(UUID.randomUUID());
        operacao = OperacaoFinanciada.associar(
                credora.getId(), UUID.randomUUID(), UUID.randomUUID(), "Associacao assistida");
    }

    private AporteCredora aporte(String valor) {
        AporteCredora a = AporteCredora.registrar(
                operacao.getId(), operacao.getEmpresaCredoraId(), new BigDecimal(valor), "key-" + valor);
        a.marcarEmProcessamento("ref-" + valor);
        return a;
    }

    @Test
    void visaoOperacionalListaAportesDeQualquerOperacaoExistente() {
        when(operacaoRepository.findById(operacao.getId())).thenReturn(Optional.of(operacao));
        when(aporteRepository.findByOperacaoIdOrderByDataCriacaoDesc(operacao.getId()))
                .thenReturn(List.of(aporte("2500.00"), aporte("100.00")));

        List<AporteCredoraView> views = useCase.executar(usuarioId, operacao.getId(), true);

        assertThat(views).hasSize(2);
        assertThat(views.get(0).status()).isEqualTo(StatusAporteCredora.EM_PROCESSAMENTO);
        assertThat(views.get(0).valor()).isEqualByComparingTo("2500.00");
        verify(empresaRepository, never()).findByUsuarioId(any());
    }

    @Test
    void credoraDonaListaAportesDaPropriaOperacao() {
        when(empresaRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(credora));
        when(operacaoRepository.findByIdAndEmpresaCredoraId(operacao.getId(), credora.getId()))
                .thenReturn(Optional.of(operacao));
        when(aporteRepository.findByOperacaoIdOrderByDataCriacaoDesc(operacao.getId()))
                .thenReturn(List.of(aporte("2500.00")));

        List<AporteCredoraView> views = useCase.executar(usuarioId, operacao.getId(), false);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).operacaoId()).isEqualTo(operacao.getId());
    }

    @Test
    void credoraAlheiaEInexistenteSaoIndistinguiveis() {
        when(empresaRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(credora));
        UUID operacaoAlheia = UUID.randomUUID();
        UUID operacaoInexistente = UUID.randomUUID();
        when(operacaoRepository.findByIdAndEmpresaCredoraId(operacaoAlheia, credora.getId()))
                .thenReturn(Optional.empty());
        when(operacaoRepository.findByIdAndEmpresaCredoraId(operacaoInexistente, credora.getId()))
                .thenReturn(Optional.empty());

        Throwable alheia = catchThrowable(() -> useCase.executar(usuarioId, operacaoAlheia, false));
        Throwable inexistente = catchThrowable(() -> useCase.executar(usuarioId, operacaoInexistente, false));

        assertThat(alheia).isInstanceOf(AporteOperacaoNaoEncontradaException.class);
        assertThat(inexistente).isInstanceOf(AporteOperacaoNaoEncontradaException.class);
        assertThat(alheia.getMessage()).isEqualTo(inexistente.getMessage());
        assertThat(alheia.getMessage()).doesNotContain(operacaoAlheia.toString());
        verify(aporteRepository, never()).findByOperacaoIdOrderByDataCriacaoDesc(any());
    }

    @Test
    void usuarioSemCredoraRecebe404Neutro() {
        when(empresaRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.empty());

        Throwable erro = catchThrowable(() -> useCase.executar(usuarioId, operacao.getId(), false));

        assertThat(erro).isInstanceOf(AporteOperacaoNaoEncontradaException.class);
        verify(aporteRepository, never()).findByOperacaoIdOrderByDataCriacaoDesc(any());
    }

    @Test
    void operacaoInexistenteNaVisaoOperacionalRecebe404Neutro() {
        UUID inexistente = UUID.randomUUID();
        when(operacaoRepository.findById(inexistente)).thenReturn(Optional.empty());

        Throwable erro = catchThrowable(() -> useCase.executar(usuarioId, inexistente, true));

        assertThat(erro)
                .isInstanceOf(AporteOperacaoNaoEncontradaException.class)
                .hasMessageNotContaining(inexistente.toString());
    }

    @Test
    void listaVaziaEValidaQuandoOperacaoExisteNoEscopo() {
        when(operacaoRepository.findById(operacao.getId())).thenReturn(Optional.of(operacao));
        when(aporteRepository.findByOperacaoIdOrderByDataCriacaoDesc(operacao.getId()))
                .thenReturn(List.of());

        assertThat(useCase.executar(usuarioId, operacao.getId(), true)).isEmpty();
    }
}
