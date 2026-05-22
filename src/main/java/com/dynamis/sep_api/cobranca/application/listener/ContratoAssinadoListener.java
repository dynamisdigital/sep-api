package com.dynamis.sep_api.cobranca.application.listener;

import com.dynamis.sep_api.cobranca.application.dto.GerarAgendaPagamentoCommand;
import com.dynamis.sep_api.cobranca.application.port.out.PropostaCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.port.out.PropostaCobrancaView;
import com.dynamis.sep_api.cobranca.application.service.calculo.ParametrosCobrancaProperties;
import com.dynamis.sep_api.cobranca.application.usecase.GerarAgendaPagamentoUseCase;
import com.dynamis.sep_api.contratos.domain.event.ContratoAssinadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

/**
 * Liga {@link ContratoAssinadoEvent} (Sprint 11) a geracao automatica da agenda de pagamento
 * (Sprint 12 Task 12.3).
 *
 * <p>{@link TransactionalEventListener} em {@link TransactionPhase#AFTER_COMMIT} garante que a
 * geracao so dispara apos a assinatura ser commitada. {@link Propagation#REQUIRES_NEW} abre
 * transacao independente — falha aqui nao reverte a assinatura ja persistida. Padrao identico ao
 * {@code ContratoAceitoListener} (Sprint 11) e {@code PldOrchestrationListener} (Sprint 7).
 *
 * <p>Consome a porta {@link PropostaCobrancaQueryPort} (ADR 0007) pra evitar dependencia direta
 * de {@code credito.infrastructure}.
 */
@Component
@ConditionalOnProperty(name = "app.cobranca.auto-geracao-pos-assinatura", havingValue = "true", matchIfMissing = true)
public class ContratoAssinadoListener {

    private static final Logger log = LoggerFactory.getLogger(ContratoAssinadoListener.class);

    private final GerarAgendaPagamentoUseCase useCase;
    private final PropostaCobrancaQueryPort propostaQueryPort;
    private final ParametrosCobrancaProperties properties;

    public ContratoAssinadoListener(
            GerarAgendaPagamentoUseCase useCase,
            PropostaCobrancaQueryPort propostaQueryPort,
            ParametrosCobrancaProperties properties) {
        this.useCase = useCase;
        this.propostaQueryPort = propostaQueryPort;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAssinar(ContratoAssinadoEvent event) {
        try {
            PropostaCobrancaView proposta = propostaQueryPort
                    .buscarPorId(event.propostaId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Proposta nao encontrada para gerar agenda: " + event.propostaId()));

            BigDecimal taxaMensal = proposta.taxaJurosMensal().orElseGet(() -> {
                log.warn(
                        "Proposta {} sem taxa estruturada — usando default config {} (contratoId={}). "
                                + "Sprint posterior deve persistir taxa na proposta antes da assinatura.",
                        proposta.id(),
                        properties.getTaxaJurosMensalDefault(),
                        event.contratoId());
                return properties.getTaxaJurosMensalDefault();
            });

            GerarAgendaPagamentoCommand cmd = new GerarAgendaPagamentoCommand(
                    event.contratoId(),
                    event.propostaId(),
                    event.tomadorId(),
                    proposta.valorSolicitado(),
                    proposta.prazoMeses(),
                    taxaMensal,
                    event.dataAssinatura().toLocalDate(),
                    properties.getAmortizacaoDefault());

            useCase.executar(cmd);
        } catch (RuntimeException ex) {
            // Assinatura ja commitada; falha aqui nao desfaz a assinatura. Operador deve
            // reprocessar a geracao via endpoint manual (futuro). Log com payload completo
            // do evento pra correlacao operacional.
            log.warn(
                    "Falha ao gerar agenda apos assinatura — reprocessamento manual necessario."
                            + " contratoId={} propostaId={} tomadorId={} dataAssinatura={} excecao={}: {}",
                    event.contratoId(),
                    event.propostaId(),
                    event.tomadorId(),
                    event.dataAssinatura(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
        }
    }
}
