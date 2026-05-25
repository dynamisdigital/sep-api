package com.dynamis.sep_api.cobranca.application.listener;

import com.dynamis.sep_api.cobranca.application.port.out.NotificationProvider;
import com.dynamis.sep_api.cobranca.application.port.out.dto.Notificacao;
import com.dynamis.sep_api.cobranca.domain.event.RenegociacaoPropostaEvent;
import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RenegociacaoRepository;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Notifica tomador via email + SMS apos {@link RenegociacaoPropostaEvent} (Sprint 13 Task 13.6 —
 * fix review manual).
 *
 * <p>{@code AFTER_COMMIT} + {@code REQUIRES_NEW}: garante que a renegociacao esta persistida
 * antes de notificar e falha de provider nao reverte a proposta. Padrao identico aos listeners
 * da Sprint 7/12.
 *
 * <p>Telefone do tomador eh {@code null} nesta fase (PRD §RF-01: {@code Usuario} so expoe email).
 * Adapter Zenvia retorna {@code FALHA} silenciosa quando destinatario vazio — coerente com o
 * comportamento do ParcelaAtrasouListener.
 */
@Component
public class RenegociacaoPropostaListener {

    private static final Logger log = LoggerFactory.getLogger(RenegociacaoPropostaListener.class);
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("pt", "BR"));

    private final RenegociacaoRepository renegociacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final List<NotificationProvider> providers;

    public RenegociacaoPropostaListener(
            RenegociacaoRepository renegociacaoRepository,
            UsuarioRepository usuarioRepository,
            List<NotificationProvider> providers) {
        this.renegociacaoRepository = renegociacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.providers = providers;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoProporRenegociacao(RenegociacaoPropostaEvent event) {
        Renegociacao renegociacao =
                renegociacaoRepository.findById(event.renegociacaoId()).orElse(null);
        if (renegociacao == null) {
            log.warn("RenegociacaoPropostaListener: renegociacao {} sumiu", event.renegociacaoId());
            return;
        }
        String email = usuarioRepository
                .findById(event.tomadorId())
                // PRD §RF-01: Usuario.username eh o email validado.
                .map(Usuario::getUsername)
                .orElse(null);
        Map<String, Object> vars = montarVariaveis(renegociacao);
        if (email != null) {
            enviar(CanalNotificacao.EMAIL, email, "renegociacao-proposta", vars);
        }
        // SMS: telefone do tomador ainda nao exposto no dominio (PRD §RF-01); skip silencioso ate
        // Sprint futura ampliar Usuario com telefone. Mantemos o template SMS pra evolucao.
    }

    private Map<String, Object> montarVariaveis(Renegociacao renegociacao) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("numeroParcela", "—");
        vars.put("novoValor", "R$ " + renegociacao.getNovoValorParcela());
        vars.put("numeroParcelas", renegociacao.getNumeroParcelas());
        vars.put("novoVencimento", renegociacao.getNovoVencimento().format(DATA_BR));
        vars.put("dataExpiracao", renegociacao.getDataExpiracao().toLocalDate().format(DATA_BR));
        return vars;
    }

    private void enviar(CanalNotificacao canal, String destinatario, String template, Map<String, Object> vars) {
        providers.stream().filter(p -> p.suporta(canal)).findFirst().ifPresent(p -> {
            try {
                p.enviar(new Notificacao(canal, destinatario, template, vars, null));
            } catch (RuntimeException e) {
                log.warn("RenegociacaoPropostaListener: envio {} falhou (template={})", canal, template, e);
            }
        });
    }
}
