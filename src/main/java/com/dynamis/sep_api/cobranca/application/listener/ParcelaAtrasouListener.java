package com.dynamis.sep_api.cobranca.application.listener;

import com.dynamis.sep_api.cobranca.application.dto.EscalarCobrancaCommand;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.usecase.EscalarCobrancaUseCase;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaAtrasouEvent;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Liga {@link ParcelaAtrasouEvent} (Sprint 12 Task 12.5) ao workflow de cobranca (Sprint 13 Task
 * 13.4).
 *
 * <p>{@link TransactionalEventListener#phase()} {@link TransactionPhase#AFTER_COMMIT} garante que
 * a transicao ATRASADA esta persistida antes de disparar notificacoes. {@link Propagation#REQUIRES_NEW}
 * abre transacao independente — falha de notificacao nao reverte a transicao de status. Padrao
 * identico aos listeners da Sprint 7 (PldOrchestrationListener) e Sprint 12 (CobrancaAuditListener).
 */
@Component
public class ParcelaAtrasouListener {

    private static final Logger log = LoggerFactory.getLogger(ParcelaAtrasouListener.class);
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("pt", "BR"));

    private final EscalarCobrancaUseCase useCase;
    private final ContratoCobrancaQueryPort contratoQuery;
    private final UsuarioRepository usuarioRepository;
    private final ParcelaCobrancaRepository parcelaRepository;

    public ParcelaAtrasouListener(
            EscalarCobrancaUseCase useCase,
            ContratoCobrancaQueryPort contratoQuery,
            UsuarioRepository usuarioRepository,
            ParcelaCobrancaRepository parcelaRepository) {
        this.useCase = useCase;
        this.contratoQuery = contratoQuery;
        this.usuarioRepository = usuarioRepository;
        this.parcelaRepository = parcelaRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAtrasarParcela(ParcelaAtrasouEvent event) {
        Optional<UUID> tomadorIdOpt = contratoQuery.tomadorIdDoContrato(event.contratoId());
        if (tomadorIdOpt.isEmpty()) {
            log.warn(
                    "Sem tomadorId pra contrato={} (parcela={}); nao escalando dia 0",
                    event.contratoId(),
                    event.parcelaId());
            return;
        }
        String email = usuarioRepository
                .findById(tomadorIdOpt.get())
                .map(Usuario::getUsername)
                .orElse(null);
        EscalarCobrancaCommand command = new EscalarCobrancaCommand(
                event.parcelaId(), 0, email, null, variaveis(event), MDC.get("correlationId"));
        useCase.escalar(command);
    }

    private Map<String, Object> variaveis(ParcelaAtrasouEvent event) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("numeroParcela", event.numero());
        vars.put("diasAtraso", 0);
        vars.put("dataVencimento", event.dataVencimento().format(DATA_BR));
        parcelaRepository
                .findById(event.parcelaId())
                .map(ParcelaCobranca::valorTotal)
                .ifPresent(v -> vars.put("valor", "R$ " + v));
        return vars;
    }
}
