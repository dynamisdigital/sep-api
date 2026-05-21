package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.application.port.out.TemplateContratoEngine;
import com.dynamis.sep_api.contratos.application.port.out.dto.ClausulaRenderizada;
import com.dynamis.sep_api.contratos.application.port.out.dto.TemplateContratoRequest;
import com.dynamis.sep_api.contratos.application.port.out.dto.TemplateContratoResponse;
import com.dynamis.sep_api.contratos.application.service.ContextoContratoBuilder;
import com.dynamis.sep_api.contratos.application.service.HashContratoService;
import com.dynamis.sep_api.contratos.application.usecase.command.GerarContratoCommand;
import com.dynamis.sep_api.contratos.domain.event.ContratoGeradoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoNovaVersaoEvent;
import com.dynamis.sep_api.contratos.domain.exception.ContratoEstadoInvalidoException;
import com.dynamis.sep_api.contratos.domain.exception.PropostaNaoAprovadaException;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import com.dynamis.sep_api.credito.application.usecase.ConsultarPropostaUseCase;
import com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.DecisaoParecer;
import com.dynamis.sep_api.credito.domain.vo.Money;
import com.dynamis.sep_api.credito.domain.vo.TipoOperacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GerarContratoUseCaseTest {

    private static final String HASH = "0".repeat(64);

    @Mock
    private ConsultarPropostaUseCase consultarPropostaUseCase;

    @Mock
    private ContratoRepository contratoRepository;

    @Mock
    private ContextoContratoBuilder contextoBuilder;

    @Mock
    private TemplateContratoEngine templateEngine;

    @Mock
    private HashContratoService hashService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GerarContratoUseCase useCase;

    private PropostaCredito proposta;
    private UUID parecerId;

    @BeforeEach
    void setup() {
        proposta = PropostaCredito.criar(
                UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("10000"), 12);
        proposta.aplicarSugestaoMotor(com.dynamis.sep_api.credito.domain.vo.StatusProposta.PRE_APROVADA);
        proposta.registrarDecisaoManual(DecisaoParecer.APROVAR);
        parecerId = UUID.randomUUID();
    }

    @Test
    void executar_propostaInexistente_lancaNaoEncontrada() {
        UUID id = UUID.randomUUID();
        when(consultarPropostaUseCase.executar(id)).thenThrow(new PropostaNaoEncontradaException(id));

        assertThatThrownBy(() -> useCase.executar(new GerarContratoCommand(id, parecerId)))
                .isInstanceOf(PropostaNaoEncontradaException.class);
    }

    @Test
    void executar_propostaNaoAprovada_lanca422() {
        PropostaCredito naoAprovada = PropostaCredito.criar(
                UUID.randomUUID(), UUID.randomUUID(), TipoOperacao.CAPITAL_GIRO, Money.brl("100"), 6);
        when(consultarPropostaUseCase.executar(naoAprovada.getId())).thenReturn(naoAprovada);

        assertThatThrownBy(() -> useCase.executar(new GerarContratoCommand(naoAprovada.getId(), parecerId)))
                .isInstanceOf(PropostaNaoAprovadaException.class);
    }

    @Test
    void executar_criacaoInicial_geraContratoEmAguardandoAceiteEPublicaEventoGerado() {
        mockarRenderizacao();
        when(consultarPropostaUseCase.executar(proposta.getId())).thenReturn(proposta);
        when(contratoRepository.findByPropostaId(proposta.getId())).thenReturn(Optional.empty());
        when(contratoRepository.save(any(Contrato.class))).thenAnswer(inv -> inv.getArgument(0));

        Contrato resultado = useCase.executar(new GerarContratoCommand(proposta.getId(), parecerId));

        assertThat(resultado.getStatus()).isEqualTo(StatusFormalizacao.AGUARDANDO_ACEITE);
        assertThat(resultado.getVersoes()).hasSize(1);
        assertThat(resultado.getVersoes().get(0).getNumero()).isEqualTo(1);
        assertThat(resultado.getVersoes().get(0).getParecerOrigemId()).isEqualTo(parecerId);
        assertThat(resultado.getVersoes().get(0).getClausulas()).hasSize(2);
        verify(eventPublisher, times(1)).publishEvent(argThat((Object e) -> e instanceof ContratoGeradoEvent));
    }

    @Test
    void executar_regeneracaoComNovoParecer_publicaEventoNovaVersao() {
        UUID outroParecer = UUID.randomUUID();
        Contrato existente = Contrato.criar(proposta.getId(), proposta.getTomadorId(), TipoContrato.MUTUO);
        existente.adicionarVersao("v1", HASH, outroParecer);

        mockarRenderizacao();
        when(consultarPropostaUseCase.executar(proposta.getId())).thenReturn(proposta);
        when(contratoRepository.findByPropostaId(proposta.getId())).thenReturn(Optional.of(existente));
        when(contratoRepository.save(any(Contrato.class))).thenAnswer(inv -> inv.getArgument(0));

        Contrato resultado = useCase.executar(new GerarContratoCommand(proposta.getId(), parecerId));

        assertThat(resultado.getVersoes()).hasSize(2);
        assertThat(resultado.getVersoes().get(1).getNumero()).isEqualTo(2);
        assertThat(resultado.getVersoes().get(1).getParecerOrigemId()).isEqualTo(parecerId);
        verify(eventPublisher, times(1)).publishEvent(argThat((Object e) -> e instanceof ContratoNovaVersaoEvent));
    }

    @Test
    void executar_replayDoMesmoParecer_idempotenteRetornaContratoSemNovaVersao() {
        // Replay do PropostaAprovadaEvent: mesma proposta, mesmo parecerId — versao vigente ja
        // foi gerada por esse parecer. UseCase faz short-circuit.
        Contrato existente = Contrato.criar(proposta.getId(), proposta.getTomadorId(), TipoContrato.MUTUO);
        existente.adicionarVersao("v1 ja gerada por esse parecer", HASH, parecerId);

        when(consultarPropostaUseCase.executar(proposta.getId())).thenReturn(proposta);
        when(contratoRepository.findByPropostaId(proposta.getId())).thenReturn(Optional.of(existente));

        Contrato resultado = useCase.executar(new GerarContratoCommand(proposta.getId(), parecerId));

        assertThat(resultado).isSameAs(existente);
        assertThat(resultado.getVersoes()).hasSize(1);
        verify(contratoRepository, never()).save(any(Contrato.class));
        verify(eventPublisher, never()).publishEvent(any());
        verify(templateEngine, never()).renderizar(any());
    }

    @Test
    void executar_comandoManualSemParecer_naoAplicaIdempotencia() {
        // Comando manual (parecerOrigemId=null) — regeneracao explicita, sem short-circuit.
        Contrato existente = Contrato.criar(proposta.getId(), proposta.getTomadorId(), TipoContrato.MUTUO);
        existente.adicionarVersao("v1", HASH);

        mockarRenderizacao();
        when(consultarPropostaUseCase.executar(proposta.getId())).thenReturn(proposta);
        when(contratoRepository.findByPropostaId(proposta.getId())).thenReturn(Optional.of(existente));
        when(contratoRepository.save(any(Contrato.class))).thenAnswer(inv -> inv.getArgument(0));

        Contrato resultado = useCase.executar(GerarContratoCommand.manual(proposta.getId()));

        assertThat(resultado.getVersoes()).hasSize(2);
        verify(eventPublisher, times(1)).publishEvent(argThat((Object e) -> e instanceof ContratoNovaVersaoEvent));
    }

    @Test
    void executar_contratoAceito_rejeita() {
        Contrato existente = Contrato.criar(proposta.getId(), proposta.getTomadorId(), TipoContrato.MUTUO);
        existente.adicionarVersao("v1", HASH);
        existente.marcarAceito();

        when(consultarPropostaUseCase.executar(proposta.getId())).thenReturn(proposta);
        when(contratoRepository.findByPropostaId(proposta.getId())).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> useCase.executar(new GerarContratoCommand(proposta.getId(), parecerId)))
                .isInstanceOf(ContratoEstadoInvalidoException.class);
    }

    @Test
    void executar_hashCalculadoDoConteudoFinal() {
        mockarRenderizacao();
        when(consultarPropostaUseCase.executar(proposta.getId())).thenReturn(proposta);
        when(contratoRepository.findByPropostaId(proposta.getId())).thenReturn(Optional.empty());
        when(contratoRepository.save(any(Contrato.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.executar(new GerarContratoCommand(proposta.getId(), parecerId));

        verify(hashService).calcular("CONTEUDO RENDERIZADO");
        verify(eventPublisher)
                .publishEvent(argThat((Object e) ->
                        e instanceof ContratoGeradoEvent ev && ev.hashSha256().equals(HASH)));
    }

    private void mockarRenderizacao() {
        when(contextoBuilder.construir(proposta))
                .thenReturn(Map.of(
                        "propostaId", proposta.getId().toString(),
                        "tomadorId", proposta.getTomadorId().toString(),
                        "tipoOperacao", "CAPITAL_GIRO",
                        "valorSolicitado", "10.000,00",
                        "moeda", "BRL",
                        "prazoMeses", 12,
                        "dataGeracao", "20/05/2026",
                        "clausulasPadrao", "CLAUSULA 1 - OBJETO\nTexto.\nCLAUSULA 2 - FORO\nSP.\n"));
        when(templateEngine.renderizar(any(TemplateContratoRequest.class)))
                .thenReturn(new TemplateContratoResponse(
                        "CONTEUDO RENDERIZADO",
                        List.of(
                                new ClausulaRenderizada(1, "OBJETO", "Texto."),
                                new ClausulaRenderizada(2, "FORO", "SP."))));
        when(hashService.calcular(anyString())).thenReturn(HASH);
    }
}
