package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.GerarSugestoesMatchingResult;
import com.dynamis.sep_api.credores.application.port.out.ConsultarContratoParaCarteiraCredoraPort;
import com.dynamis.sep_api.credores.application.port.out.ContratoCarteiraView;
import com.dynamis.sep_api.credores.application.service.ValidadorElegibilidadeMatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.event.MatchingCredoraSugeridoEvent;
import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import com.dynamis.sep_api.credores.domain.model.MatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.model.OperacaoFinanciada;
import com.dynamis.sep_api.credores.domain.model.OportunidadeInvestimento;
import com.dynamis.sep_api.credores.domain.model.PerfilCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusMatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.vo.TipoCredora;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.MatchingCredoraOperacaoRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OperacaoFinanciadaRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OportunidadeInvestimentoRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.PerfilCredoraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Geracao de sugestoes de matching por snapshot em lote (Sprint 30 Task 30.3): so pares elegiveis
 * viram sugestao, duplicidade (inclusive REJEITADA) bloqueia, consultas sao por lote (sem chamada
 * por item) e auditoria e emitida uma unica vez por sugestao nova.
 */
class GerarSugestoesMatchingCredoraUseCaseTest {

    private static final UUID ATOR = UUID.randomUUID();

    private OperacaoFinanciadaRepository operacaoRepository;
    private EmpresaCredoraRepository empresaCredoraRepository;
    private PerfilCredoraRepository perfilCredoraRepository;
    private OportunidadeInvestimentoRepository oportunidadeRepository;
    private MatchingCredoraOperacaoRepository matchingRepository;
    private ConsultarContratoParaCarteiraCredoraPort contratoPort;
    private ApplicationEventPublisher eventPublisher;
    private GerarSugestoesMatchingCredoraUseCase useCase;

    private EmpresaCredora credora;
    private OportunidadeInvestimento oportunidade;
    private OperacaoFinanciada operacao;
    private UUID contratoId;

    @BeforeEach
    void setup() {
        operacaoRepository = mock(OperacaoFinanciadaRepository.class);
        empresaCredoraRepository = mock(EmpresaCredoraRepository.class);
        perfilCredoraRepository = mock(PerfilCredoraRepository.class);
        oportunidadeRepository = mock(OportunidadeInvestimentoRepository.class);
        matchingRepository = mock(MatchingCredoraOperacaoRepository.class);
        contratoPort = mock(ConsultarContratoParaCarteiraCredoraPort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new GerarSugestoesMatchingCredoraUseCase(
                operacaoRepository,
                empresaCredoraRepository,
                perfilCredoraRepository,
                oportunidadeRepository,
                matchingRepository,
                contratoPort,
                new ValidadorElegibilidadeMatchingCredoraOperacao(),
                eventPublisher);

        contratoId = UUID.randomUUID();
        credora = EmpresaCredora.cadastrar(UUID.randomUUID(), UUID.randomUUID(), "12345678000199", "Credora SA");
        credora.registrarElegivel();
        oportunidade =
                OportunidadeInvestimento.criar(UUID.randomUUID(), contratoId, new BigDecimal("10000.00"), 12, null);
        operacao =
                OperacaoFinanciada.associar(credora.getId(), contratoId, oportunidade.getId(), "Associacao assistida");

        when(operacaoRepository.buscarAssociadasParaMatchingForUpdate(any(), any()))
                .thenReturn(List.of(operacao));
        when(empresaCredoraRepository.findAllById(any())).thenReturn(List.of(credora));
        when(perfilCredoraRepository.findAllByEmpresaCredoraIdIn(anyCollection()))
                .thenReturn(
                        List.of(PerfilCredora.criar(credora.getId(), TipoCredora.EMPRESA, new BigDecimal("50000.00"))));
        when(oportunidadeRepository.findAllById(any())).thenReturn(List.of(oportunidade));
        when(matchingRepository.findAllByOperacaoIdIn(anyCollection())).thenReturn(List.of());
        when(contratoPort.consultarPorIds(anyCollection()))
                .thenReturn(List.of(new ContratoCarteiraView(contratoId, UUID.randomUUID(), "ASSINADO")));
        when(matchingRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void parElegivelGeraSugestaoEAuditaUmaVez() {
        GerarSugestoesMatchingResult resultado = useCase.executar(ATOR);

        assertThat(resultado.sugestoesNovas()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MatchingCredoraOperacao>> salvas = ArgumentCaptor.forClass(List.class);
        verify(matchingRepository).saveAll(salvas.capture());
        MatchingCredoraOperacao sugestao = salvas.getValue().get(0);
        assertThat(sugestao.getStatus()).isEqualTo(StatusMatchingCredoraOperacao.SUGERIDA);
        assertThat(sugestao.getEmpresaCredoraId()).isEqualTo(credora.getId());
        assertThat(sugestao.getOperacaoId()).isEqualTo(operacao.getId());
        assertThat(sugestao.getValorElegivel()).isEqualByComparingTo("10000.00");
        assertThat(sugestao.getCriteriosSnapshot()).contains("CAPACIDADE_COMPORTA_VALOR");

        ArgumentCaptor<MatchingCredoraSugeridoEvent> evento =
                ArgumentCaptor.forClass(MatchingCredoraSugeridoEvent.class);
        verify(eventPublisher, times(1)).publishEvent(evento.capture());
        assertThat(evento.getValue().matchingId()).isEqualTo(sugestao.getId());
        assertThat(evento.getValue().operacaoId()).isEqualTo(operacao.getId());
        assertThat(evento.getValue().empresaCredoraId()).isEqualTo(credora.getId());
        assertThat(evento.getValue().usuarioId()).isEqualTo(ATOR);
    }

    @Test
    void parComMatchingExistenteNaoEDuplicado() {
        when(matchingRepository.findAllByOperacaoIdIn(anyCollection())).thenReturn(List.of(sugestaoExistente()));

        GerarSugestoesMatchingResult resultado = useCase.executar(ATOR);

        assertThat(resultado.sugestoesNovas()).isZero();
        verify(matchingRepository, never()).saveAll(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void parRejeitadoNaoVoltaASerSugerido() {
        MatchingCredoraOperacao rejeitado = sugestaoExistente();
        rejeitado.rejeitar(UUID.randomUUID(), "fora do apetite");
        when(matchingRepository.findAllByOperacaoIdIn(anyCollection())).thenReturn(List.of(rejeitado));

        GerarSugestoesMatchingResult resultado = useCase.executar(ATOR);

        assertThat(resultado.sugestoesNovas()).isZero();
        verify(matchingRepository, never()).saveAll(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void credoraForaDeAtivaElegivelNaoGeraSugestao() {
        EmpresaCredora pendente =
                EmpresaCredora.cadastrar(UUID.randomUUID(), UUID.randomUUID(), "98765432000188", "Pendente SA");
        OperacaoFinanciada operacaoPendente =
                OperacaoFinanciada.associar(pendente.getId(), contratoId, oportunidade.getId(), "Associacao assistida");
        when(operacaoRepository.buscarAssociadasParaMatchingForUpdate(any(), any()))
                .thenReturn(List.of(operacaoPendente));
        when(empresaCredoraRepository.findAllById(any())).thenReturn(List.of(pendente));

        GerarSugestoesMatchingResult resultado = useCase.executar(ATOR);

        assertThat(resultado.sugestoesNovas()).isZero();
        verify(matchingRepository, never()).saveAll(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void contratoNaoAssinadoNaoGeraSugestao() {
        when(contratoPort.consultarPorIds(anyCollection()))
                .thenReturn(List.of(new ContratoCarteiraView(contratoId, UUID.randomUUID(), "PENDENTE_ASSINATURA")));

        GerarSugestoesMatchingResult resultado = useCase.executar(ATOR);

        assertThat(resultado.sugestoesNovas()).isZero();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void operacaoSemOportunidadeNaoGeraSugestao() {
        OperacaoFinanciada semOportunidade =
                OperacaoFinanciada.associar(credora.getId(), contratoId, null, "Associacao assistida");
        when(operacaoRepository.buscarAssociadasParaMatchingForUpdate(any(), any()))
                .thenReturn(List.of(semOportunidade));
        when(oportunidadeRepository.findAllById(any())).thenReturn(List.of());

        GerarSugestoesMatchingResult resultado = useCase.executar(ATOR);

        assertThat(resultado.sugestoesNovas()).isZero();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void perfilAusenteNaoBloqueiaSugestao() {
        when(perfilCredoraRepository.findAllByEmpresaCredoraIdIn(anyCollection()))
                .thenReturn(List.of());

        GerarSugestoesMatchingResult resultado = useCase.executar(ATOR);

        assertThat(resultado.sugestoesNovas()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MatchingCredoraOperacao>> salvas = ArgumentCaptor.forClass(List.class);
        verify(matchingRepository).saveAll(salvas.capture());
        assertThat(salvas.getValue().get(0).getCriteriosSnapshot()).doesNotContain("CAPACIDADE_COMPORTA_VALOR");
    }

    @Test
    void loteNaoConsultaPorItem() {
        EmpresaCredora outraCredora =
                EmpresaCredora.cadastrar(UUID.randomUUID(), UUID.randomUUID(), "11222333000144", "Outra SA");
        outraCredora.registrarElegivel();
        UUID outroContratoId = UUID.randomUUID();
        OportunidadeInvestimento outraOportunidade =
                OportunidadeInvestimento.criar(UUID.randomUUID(), outroContratoId, new BigDecimal("2000.00"), 6, null);
        OperacaoFinanciada outraOperacao = OperacaoFinanciada.associar(
                outraCredora.getId(), outroContratoId, outraOportunidade.getId(), "Associacao assistida");

        when(operacaoRepository.buscarAssociadasParaMatchingForUpdate(any(), any()))
                .thenReturn(List.of(operacao, outraOperacao));
        when(empresaCredoraRepository.findAllById(any())).thenReturn(List.of(credora, outraCredora));
        when(oportunidadeRepository.findAllById(any())).thenReturn(List.of(oportunidade, outraOportunidade));
        when(contratoPort.consultarPorIds(anyCollection()))
                .thenReturn(List.of(
                        new ContratoCarteiraView(contratoId, UUID.randomUUID(), "ASSINADO"),
                        new ContratoCarteiraView(outroContratoId, UUID.randomUUID(), "ASSINADO")));

        GerarSugestoesMatchingResult resultado = useCase.executar(ATOR);

        assertThat(resultado.sugestoesNovas()).isEqualTo(2);
        verify(operacaoRepository, times(1)).buscarAssociadasParaMatchingForUpdate(any(), any());
        verify(empresaCredoraRepository, times(1)).findAllById(any());
        verify(perfilCredoraRepository, times(1)).findAllByEmpresaCredoraIdIn(anyCollection());
        verify(oportunidadeRepository, times(1)).findAllById(any());
        verify(matchingRepository, times(1)).findAllByOperacaoIdIn(anyCollection());
        verify(contratoPort, times(1)).consultarPorIds(anyCollection());
        verify(contratoPort, never()).consultarPorId(any());
        verify(eventPublisher, times(2)).publishEvent(any(MatchingCredoraSugeridoEvent.class));
    }

    @Test
    void misturaDeNovaEExistenteAuditaSomenteANova() {
        EmpresaCredora outraCredora =
                EmpresaCredora.cadastrar(UUID.randomUUID(), UUID.randomUUID(), "11222333000144", "Outra SA");
        outraCredora.registrarElegivel();
        UUID outroContratoId = UUID.randomUUID();
        OportunidadeInvestimento outraOportunidade =
                OportunidadeInvestimento.criar(UUID.randomUUID(), outroContratoId, new BigDecimal("2000.00"), 6, null);
        OperacaoFinanciada outraOperacao = OperacaoFinanciada.associar(
                outraCredora.getId(), outroContratoId, outraOportunidade.getId(), "Associacao assistida");

        when(operacaoRepository.buscarAssociadasParaMatchingForUpdate(any(), any()))
                .thenReturn(List.of(operacao, outraOperacao));
        when(empresaCredoraRepository.findAllById(any())).thenReturn(List.of(credora, outraCredora));
        when(oportunidadeRepository.findAllById(any())).thenReturn(List.of(oportunidade, outraOportunidade));
        when(matchingRepository.findAllByOperacaoIdIn(anyCollection())).thenReturn(List.of(sugestaoExistente()));
        when(contratoPort.consultarPorIds(anyCollection()))
                .thenReturn(List.of(
                        new ContratoCarteiraView(contratoId, UUID.randomUUID(), "ASSINADO"),
                        new ContratoCarteiraView(outroContratoId, UUID.randomUUID(), "ASSINADO")));

        GerarSugestoesMatchingResult resultado = useCase.executar(ATOR);

        assertThat(resultado.sugestoesNovas()).isEqualTo(1);
        verify(eventPublisher, times(1)).publishEvent(any(MatchingCredoraSugeridoEvent.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MatchingCredoraOperacao>> salvas = ArgumentCaptor.forClass(List.class);
        verify(matchingRepository).saveAll(salvas.capture());
        assertThat(salvas.getValue()).hasSize(1);
        assertThat(salvas.getValue().get(0).getOperacaoId()).isEqualTo(outraOperacao.getId());
    }

    @Test
    void semCandidatosRetornaZeroSemConsultasAdicionais() {
        when(operacaoRepository.buscarAssociadasParaMatchingForUpdate(any(), any()))
                .thenReturn(List.of());

        GerarSugestoesMatchingResult resultado = useCase.executar(ATOR);

        assertThat(resultado.sugestoesNovas()).isZero();
        verifyNoInteractions(empresaCredoraRepository, matchingRepository, contratoPort, eventPublisher);
    }

    @Test
    void atorObrigatorio() {
        assertThatNullPointerException()
                .isThrownBy(() -> useCase.executar(null))
                .withMessage("atorId obrigatorio");
    }

    private MatchingCredoraOperacao sugestaoExistente() {
        return MatchingCredoraOperacao.sugerir(
                credora.getId(),
                operacao.getId(),
                new BigDecimal("10000.00"),
                List.of(com.dynamis.sep_api.credores.domain.vo.CriterioMatchingCredoraOperacao.CREDORA_ATIVA));
    }
}
