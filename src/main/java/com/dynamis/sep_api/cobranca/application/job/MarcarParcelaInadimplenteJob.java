package com.dynamis.sep_api.cobranca.application.job;

import com.dynamis.sep_api.cobranca.application.dto.EscalarCobrancaCommand;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.usecase.EscalarCobrancaUseCase;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaInadimplenteEvent;
import com.dynamis.sep_api.cobranca.domain.model.EventoCobranca;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.domain.vo.TipoEventoCobranca;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.EventoCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Job diario que marca parcelas {@code ATRASADA} com 90+ dias de atraso como {@code INADIMPLENTE}
 * e publica {@link ParcelaInadimplenteEvent} (Sprint 13 Task 13.5).
 *
 * <p>Pipeline por parcela:
 *
 * <ol>
 *   <li>Transicao {@code ATRASADA -> INADIMPLENTE} em transacao isolada via {@link
 *       TransactionTemplate} (lock pessimista + guard {@code status == ATRASADA} = idempotencia
 *       multi-instance). Persiste {@link EventoCobranca} {@code PARCELA_INADIMPLENTE} pra trilha
 *       operacional. Publica {@link ParcelaInadimplenteEvent} pra audit + fila operacional
 *       (Sprint 14). Retorna {@code true} apenas se confirmou transicao.
 *   <li>Apos confirmacao, notifica tomador via {@link EscalarCobrancaUseCase} (etapa 90 — {@code
 *       email-final + sms-firme}) e financeiro (email configurado em {@code
 *       app.cobranca.financeiro-email}). Fix review manual: notif so dispara apos transicao
 *       confirmada — race entre leitura inicial e lock nao gera mais aviso final sem transicao
 *       real (parcela paga/renegociada/apagada entre os passos NAO recebe email).
 * </ol>
 *
 * <p>Idempotente: re-execucao no mesmo dia ignora parcelas ja {@code INADIMPLENTE} (filtro de
 * status + guard pos-lock). Notificacao tambem eh idempotente via unique parcial em
 * {@code evento_cobranca}. Cron alinhado a {@code America/Sao_Paulo} (PRD §RNF-01).
 */
@Component
@ConditionalOnProperty(name = "app.cobranca.scheduling-habilitado", havingValue = "true", matchIfMissing = true)
public class MarcarParcelaInadimplenteJob {

    public static final int DIAS_INADIMPLENCIA = 90;

    private static final Logger log = LoggerFactory.getLogger(MarcarParcelaInadimplenteJob.class);
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("pt", "BR"));

    private final ParcelaCobrancaRepository parcelaRepository;
    private final EventoCobrancaRepository eventoRepository;
    private final ContratoCobrancaQueryPort contratoQuery;
    private final UsuarioRepository usuarioRepository;
    private final EscalarCobrancaUseCase escalarUseCase;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate txTemplate;
    private final Clock clock;
    private final String financeiroEmail;

    public MarcarParcelaInadimplenteJob(
            ParcelaCobrancaRepository parcelaRepository,
            EventoCobrancaRepository eventoRepository,
            ContratoCobrancaQueryPort contratoQuery,
            UsuarioRepository usuarioRepository,
            EscalarCobrancaUseCase escalarUseCase,
            ApplicationEventPublisher eventPublisher,
            TransactionTemplate txTemplate,
            Clock clock,
            @Value("${app.cobranca.financeiro-email:}") String financeiroEmail) {
        this.parcelaRepository = parcelaRepository;
        this.eventoRepository = eventoRepository;
        this.contratoQuery = contratoQuery;
        this.usuarioRepository = usuarioRepository;
        this.escalarUseCase = escalarUseCase;
        this.eventPublisher = eventPublisher;
        this.txTemplate = txTemplate;
        this.clock = clock;
        this.financeiroEmail = financeiroEmail;
    }

    @Scheduled(cron = "${app.cobranca.inadimplente-cron:0 30 2 * * *}", zone = "America/Sao_Paulo")
    public void marcarDiariamente() {
        executar();
    }

    /** Publico pra testes — controla {@code Clock} e invoca sem agendamento. */
    public int executar() {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate corte = hoje.minusDays(DIAS_INADIMPLENCIA);
        List<ParcelaCobranca> elegiveis =
                parcelaRepository.findByStatusAndDataVencimentoBefore(StatusParcela.ATRASADA, corte.plusDays(1));
        int processadas = 0;
        for (ParcelaCobranca parcela : elegiveis) {
            int dias = (int) ChronoUnit.DAYS.between(parcela.getDataVencimento(), hoje);
            if (dias < DIAS_INADIMPLENCIA) {
                continue;
            }
            try {
                if (processar(parcela.getId(), dias)) {
                    processadas++;
                }
            } catch (RuntimeException e) {
                log.warn("MarcarParcelaInadimplenteJob: falha em parcela={} dias={}", parcela.getId(), dias, e);
            }
        }
        if (processadas > 0) {
            log.info("MarcarParcelaInadimplenteJob: {} parcelas marcadas INADIMPLENTE em {}", processadas, hoje);
        }
        return processadas;
    }

    /**
     * Retorna {@code true} apenas se a transicao foi confirmada nesta execucao. Notificacoes so
     * disparam apos confirmacao pra evitar avisar tomador sobre inadimplencia que nao aconteceu
     * (race entre leitura inicial e lock — fix review manual).
     */
    private boolean processar(UUID parcelaId, int dias) {
        Boolean transicionou = txTemplate.execute(status -> transicionar(parcelaId, dias));
        if (!Boolean.TRUE.equals(transicionou)) {
            return false;
        }
        // Notificacao final eh "best effort" apos transicao confirmada — fora da tx pra falha de
        // provider nao revisar a transicao. EscalarCobrancaUseCase ja absorve falhas internas
        // como evento FALHA; capturamos RuntimeException residual aqui pra nao quebrar o loop.
        try {
            notificarTomador(parcelaId, dias);
            notificarFinanceiro(parcelaId, dias);
        } catch (RuntimeException e) {
            log.warn(
                    "MarcarParcelaInadimplenteJob: notificacao final falhou (parcela={}, dias={})", parcelaId, dias, e);
        }
        return true;
    }

    private Boolean transicionar(UUID parcelaId, int dias) {
        ParcelaCobranca atual = parcelaRepository.findByIdForUpdate(parcelaId).orElse(null);
        if (atual == null) {
            log.warn("MarcarParcelaInadimplenteJob: parcela={} sumiu antes do lock", parcelaId);
            return false;
        }
        if (atual.getStatus() != StatusParcela.ATRASADA) {
            // Outra instancia ja marcou, ou parcela mudou pra PAGA/EM_NEGOCIACAO/RENEGOCIADA
            // entre a leitura inicial e o lock — idempotente, NAO notifica.
            return false;
        }
        atual.marcarInadimplente();
        parcelaRepository.save(atual);
        // Trilha operacional persistida (fix review manual): EventoCobranca PARCELA_INADIMPLENTE
        // complementa o ParcelaInadimplenteEvent (Spring event) com registro auditavel por parcela.
        eventoRepository.save(EventoCobranca.mudancaEstado(
                parcelaId,
                TipoEventoCobranca.PARCELA_INADIMPLENTE,
                dias,
                "Transicao automatica apos " + dias + " dias de atraso",
                null,
                OffsetDateTime.now(clock)));
        UUID contratoId = atual.getAgenda().getContratoId();
        UUID tomadorId = contratoQuery.tomadorIdDoContrato(contratoId).orElse(null);
        eventPublisher.publishEvent(new ParcelaInadimplenteEvent(
                atual.getId(),
                atual.getAgenda().getId(),
                contratoId,
                tomadorId,
                atual.getNumero(),
                atual.getDataVencimento(),
                dias));
        return true;
    }

    private void notificarTomador(UUID parcelaId, int dias) {
        ParcelaCobranca parcela = parcelaRepository.findById(parcelaId).orElse(null);
        if (parcela == null) {
            return;
        }
        UUID contratoId = parcela.getAgenda().getContratoId();
        Optional<UUID> tomadorIdOpt = contratoQuery.tomadorIdDoContrato(contratoId);
        // PRD §RF-01: Usuario.username eh o email validado.
        String email = tomadorIdOpt
                .flatMap(usuarioRepository::findById)
                .map(Usuario::getUsername)
                .orElse(null);
        escalarUseCase.escalar(
                new EscalarCobrancaCommand(parcelaId, DIAS_INADIMPLENCIA, email, null, variaveis(parcela, dias), null));
    }

    /**
     * Envia a mesma comunicacao final pro email do financeiro (spec 13.5). Sem property
     * configurada, nada eh enviado — operacao silenciosa por default em dev/local.
     */
    private void notificarFinanceiro(UUID parcelaId, int dias) {
        if (financeiroEmail == null || financeiroEmail.isBlank()) {
            return;
        }
        ParcelaCobranca parcela = parcelaRepository.findById(parcelaId).orElse(null);
        if (parcela == null) {
            return;
        }
        // Variaveis incluem identificador da parcela (numero + vencimento) — suficiente pro
        // financeiro localizar o caso sem expor dados pessoais alem do necessario.
        escalarUseCase.escalar(new EscalarCobrancaCommand(
                parcelaId, DIAS_INADIMPLENCIA, financeiroEmail, null, variaveis(parcela, dias), null));
    }

    private static Map<String, Object> variaveis(ParcelaCobranca parcela, int dias) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("numeroParcela", parcela.getNumero());
        vars.put("diasAtraso", dias);
        vars.put("dataVencimento", parcela.getDataVencimento().format(DATA_BR));
        vars.put("valor", "R$ " + parcela.valorTotal());
        return vars;
    }
}
