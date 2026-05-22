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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gera a {@link AgendaPagamento} de um contrato assinado de forma idempotente (Sprint 12 Task
 * 12.3). Idempotencia tem duas camadas:
 *
 * <ol>
 *   <li>{@link AgendaPagamentoRepository#existsByContratoId(java.util.UUID)} antes do calculo —
 *       cobre concorrencia sequencial.
 *   <li>{@code UNIQUE contrato_id} no banco + {@link DataIntegrityViolationException} — cobre
 *       corrida concorrente entre listener AFTER_COMMIT e endpoint de reprocessamento manual.
 * </ol>
 *
 * <p>{@link AgendaGeradaEvent} eh publicado apenas quando uma nova agenda eh persistida; carga
 * repetida retorna a agenda existente sem republicar o evento (cf. Task 12.7 audit log).
 */
@Service
public class GerarAgendaPagamentoUseCase {

    private static final Logger log = LoggerFactory.getLogger(GerarAgendaPagamentoUseCase.class);

    private final AgendaPagamentoRepository agendaRepository;
    private final AmortizacaoDispatcher dispatcher;
    private final ParametrosCobrancaProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public GerarAgendaPagamentoUseCase(
            AgendaPagamentoRepository agendaRepository,
            AmortizacaoDispatcher dispatcher,
            ParametrosCobrancaProperties properties,
            ApplicationEventPublisher eventPublisher) {
        this.agendaRepository = agendaRepository;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AgendaPagamento executar(GerarAgendaPagamentoCommand cmd) {
        return agendaRepository
                .findByContratoId(cmd.contratoId())
                .map(existente -> {
                    log.info(
                            "Agenda ja existente para contrato {} — retornando idempotente (agendaId={})",
                            cmd.contratoId(),
                            existente.getId());
                    return existente;
                })
                .orElseGet(() -> criarPersistirEPublicar(cmd));
    }

    private AgendaPagamento criarPersistirEPublicar(GerarAgendaPagamentoCommand cmd) {
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

        try {
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
        } catch (DataIntegrityViolationException ex) {
            // Corrida concorrente: outra transacao gravou primeiro. Recupera idempotente sem
            // republicar evento (essa outra transacao ja publicou).
            log.info(
                    "Conflito UNIQUE contrato_id={} ao gerar agenda — recuperando existente (corrida)",
                    cmd.contratoId());
            return agendaRepository
                    .findByContratoId(cmd.contratoId())
                    .orElseThrow(() -> new IllegalStateException(
                            "DataIntegrityViolationException sem agenda persistida para contrato " + cmd.contratoId(),
                            ex));
        }
    }

    private static ParcelaPlanejada toPlanejada(ParcelaCalculada c) {
        return new ParcelaPlanejada(
                c.numero(), ComposicaoValor.de(c.principal(), c.juros(), c.multa(), c.encargos()), c.dataVencimento());
    }
}
