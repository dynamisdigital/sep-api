package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.GerarAgendaPagamentoCommand;
import com.dynamis.sep_api.cobranca.application.service.calculo.AmortizacaoDispatcher;
import com.dynamis.sep_api.cobranca.application.service.calculo.ParametrosCobrancaProperties;
import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ParametrosCalculo;
import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ParcelaCalculada;
import com.dynamis.sep_api.cobranca.application.service.calculo.dto.ResultadoCalculo;
import com.dynamis.sep_api.cobranca.domain.event.AgendaGeradaEvent;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento.ParcelaPlanejada;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.AgendaPagamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Gera a {@link AgendaPagamento} de um contrato assinado de forma idempotente (Sprint 12 Task
 * 12.3). Idempotencia em duas camadas:
 *
 * <ol>
 *   <li>Consulta pre-save em tx read-only — atende ao caso comum sequencial.
 *   <li>Constraint {@code UNIQUE contrato_id} + {@link DataIntegrityViolationException}: a
 *       transacao de write eh isolada via {@link TransactionTemplate}, garantindo que o rollback
 *       PostgreSQL ("current transaction is aborted") nao contamine a consulta de recuperacao.
 * </ol>
 *
 * <p>{@link AgendaGeradaEvent} eh publicado dentro da mesma transacao do save bem-sucedido.
 * Corridas concorrentes que perdem o save (UNIQUE violation) recuperam a agenda existente em uma
 * tx nova sem republicar o evento.
 */
@Service
public class GerarAgendaPagamentoUseCase {

    private static final Logger log = LoggerFactory.getLogger(GerarAgendaPagamentoUseCase.class);

    private final AgendaPagamentoRepository agendaRepository;
    private final AmortizacaoDispatcher dispatcher;
    private final ParametrosCobrancaProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate txWrite;
    private final TransactionTemplate txReadOnly;

    public GerarAgendaPagamentoUseCase(
            AgendaPagamentoRepository agendaRepository,
            AmortizacaoDispatcher dispatcher,
            ParametrosCobrancaProperties properties,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager txManager) {
        this.agendaRepository = agendaRepository;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.txWrite = new TransactionTemplate(txManager);
        this.txReadOnly = new TransactionTemplate(txManager);
        this.txReadOnly.setReadOnly(true);
    }

    public AgendaPagamento executar(GerarAgendaPagamentoCommand cmd) {
        Optional<AgendaPagamento> existente = buscarPorContrato(cmd.contratoId());
        if (existente.isPresent()) {
            log.info(
                    "Agenda ja existente para contrato {} — retornando idempotente (agendaId={})",
                    cmd.contratoId(),
                    existente.get().getId());
            return existente.get();
        }

        try {
            return criarPersistirEPublicar(cmd);
        } catch (DataIntegrityViolationException ex) {
            // Corrida concorrente: outra transacao gravou primeiro. Tx de write ja foi rolled
            // back (PostgreSQL aborta a tx no UNIQUE violation). Recupera em tx nova read-only.
            log.info(
                    "Conflito UNIQUE contrato_id={} ao gerar agenda — recuperando existente (corrida concorrente)",
                    cmd.contratoId());
            return buscarPorContrato(cmd.contratoId())
                    .orElseThrow(() -> new IllegalStateException(
                            "DataIntegrityViolationException sem agenda persistida para contrato " + cmd.contratoId(),
                            ex));
        }
    }

    private Optional<AgendaPagamento> buscarPorContrato(UUID contratoId) {
        return txReadOnly.execute(status -> agendaRepository.findByContratoIdAndAtivaTrue(contratoId));
    }

    private AgendaPagamento criarPersistirEPublicar(GerarAgendaPagamentoCommand cmd) {
        return txWrite.execute(status -> {
            ResultadoCalculo calculo = dispatcher.calcular(new ParametrosCalculo(
                    cmd.valorFinanciado(),
                    cmd.taxaMensal(),
                    cmd.numeroParcelas(),
                    cmd.dataBase(),
                    cmd.sistema(),
                    properties.getPrimeiraParcelaDias(),
                    properties.getPeriodicidadeDias()));

            List<ParcelaPlanejada> planejadas = calculo.parcelas().stream()
                    .map(GerarAgendaPagamentoUseCase::toPlanejada)
                    .toList();

            AgendaPagamento agenda = AgendaPagamento.criar(cmd.contratoId(), planejadas);
            AgendaPagamento salva = agendaRepository.saveAndFlush(agenda);
            eventPublisher.publishEvent(new AgendaGeradaEvent(
                    salva.getId(),
                    salva.getContratoId(),
                    cmd.propostaId(),
                    cmd.tomadorId(),
                    salva.getNumeroParcelas(),
                    salva.getValorTotal(),
                    salva.getDataGeracao()));
            return salva;
        });
    }

    private static ParcelaPlanejada toPlanejada(ParcelaCalculada c) {
        return new ParcelaPlanejada(
                c.numero(), ComposicaoValor.de(c.principal(), c.juros(), c.multa(), c.encargos()), c.dataVencimento());
    }
}
