package com.dynamis.sep_api.cobranca.application.job;

import com.dynamis.sep_api.cobranca.application.dto.EscalarCobrancaCommand;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.usecase.EscalarCobrancaUseCase;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaInadimplenteEvent;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
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
 * <p>Roda 30 min antes do {@link EscaladorCobrancaJob} (03:00) para garantir que parcelas que
 * cruzaram a marca dos 90 dias ja estejam {@code INADIMPLENTE} quando o escalador buscar
 * {@code ATRASADA} — assim a comunicacao da etapa 90 do workflow nao reincide sobre parcela
 * recem-marcada.
 *
 * <p>Pipeline por parcela:
 *
 * <ol>
 *   <li>Notifica tomador via {@link EscalarCobrancaUseCase} (etapa 90 — {@code email-final +
 *       sms-firme}). Falha de provider eh absorvida como {@code EventoCobranca} {@code FALHA} e
 *       <strong>nao bloqueia</strong> a transicao (spec 13.5).
 *   <li>Transicao {@code ATRASADA -> INADIMPLENTE} em transacao isolada via {@link
 *       TransactionTemplate} (lock pessimista evita race entre instancias).
 *   <li>Publica {@link ParcelaInadimplenteEvent} pra audit + fila operacional (Sprint 14).
 * </ol>
 *
 * <p>Idempotente: re-execucao no mesmo dia ignora parcelas ja {@code INADIMPLENTE} (filtro de
 * status). Cron alinhado a {@code America/Sao_Paulo} (PRD §RNF-01).
 */
@Component
@ConditionalOnProperty(name = "app.cobranca.scheduling-habilitado", havingValue = "true", matchIfMissing = true)
public class MarcarParcelaInadimplenteJob {

    public static final int DIAS_INADIMPLENCIA = 90;

    private static final Logger log = LoggerFactory.getLogger(MarcarParcelaInadimplenteJob.class);
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("pt", "BR"));

    private final ParcelaCobrancaRepository parcelaRepository;
    private final ContratoCobrancaQueryPort contratoQuery;
    private final UsuarioRepository usuarioRepository;
    private final EscalarCobrancaUseCase escalarUseCase;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate txTemplate;
    private final Clock clock;

    public MarcarParcelaInadimplenteJob(
            ParcelaCobrancaRepository parcelaRepository,
            ContratoCobrancaQueryPort contratoQuery,
            UsuarioRepository usuarioRepository,
            EscalarCobrancaUseCase escalarUseCase,
            ApplicationEventPublisher eventPublisher,
            TransactionTemplate txTemplate,
            Clock clock) {
        this.parcelaRepository = parcelaRepository;
        this.contratoQuery = contratoQuery;
        this.usuarioRepository = usuarioRepository;
        this.escalarUseCase = escalarUseCase;
        this.eventPublisher = eventPublisher;
        this.txTemplate = txTemplate;
        this.clock = clock;
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
                processar(parcela.getId(), dias);
                processadas++;
            } catch (RuntimeException e) {
                log.warn("MarcarParcelaInadimplenteJob: falha em parcela={} dias={}", parcela.getId(), dias, e);
            }
        }
        if (processadas > 0) {
            log.info("MarcarParcelaInadimplenteJob: {} parcelas marcadas INADIMPLENTE em {}", processadas, hoje);
        }
        return processadas;
    }

    private void processar(UUID parcelaId, int dias) {
        // Passo 1: notifica via use case (tx propria; absorve falhas como evento FALHA).
        tentarNotificarFinal(parcelaId, dias);
        // Passo 2: transicao + evento em tx separada — provider que falhou nao bloqueia transicao.
        txTemplate.executeWithoutResult(status -> transicionar(parcelaId, dias));
    }

    private void tentarNotificarFinal(UUID parcelaId, int dias) {
        try {
            ParcelaCobranca parcela = parcelaRepository.findById(parcelaId).orElse(null);
            if (parcela == null) {
                return;
            }
            UUID contratoId = parcela.getAgenda().getContratoId();
            Optional<UUID> tomadorIdOpt = contratoQuery.tomadorIdDoContrato(contratoId);
            String email = tomadorIdOpt
                    // PRD §RF-01: Usuario.username eh o email validado.
                    .flatMap(usuarioRepository::findById)
                    .map(Usuario::getUsername)
                    .orElse(null);
            Map<String, Object> vars = new HashMap<>();
            vars.put("numeroParcela", parcela.getNumero());
            vars.put("diasAtraso", dias);
            vars.put("dataVencimento", parcela.getDataVencimento().format(DATA_BR));
            vars.put("valor", "R$ " + parcela.valorTotal());
            // Fix code review Task 13.5: workflow do EscalarCobrancaUseCase usa match exato por dia
            // (resolver.etapaParaDia). Se passassemos {@code dias} real (ex.: 97), nenhuma etapa
            // casaria e o use case sairia em semEtapa() sem notificar. Fixamos {@link
            // #DIAS_INADIMPLENCIA} pra garantir que a etapa 90 do YAML ({@code email-final +
            // sms-firme}) sempre dispare na transicao pra INADIMPLENTE, mesmo quando o job
            // pega parcelas com varios dias acima do limite (ex.: catch-up apos janela inativa).
            escalarUseCase.escalar(new EscalarCobrancaCommand(parcelaId, DIAS_INADIMPLENCIA, email, null, vars, null));
        } catch (RuntimeException e) {
            // Notificacao final eh "best effort" (spec 13.5: nao bloqueia transicao).
            log.warn(
                    "MarcarParcelaInadimplenteJob: notificacao final falhou (parcela={}, dias={})", parcelaId, dias, e);
        }
    }

    private void transicionar(UUID parcelaId, int dias) {
        ParcelaCobranca atual = parcelaRepository.findByIdForUpdate(parcelaId).orElse(null);
        if (atual == null) {
            log.warn("MarcarParcelaInadimplenteJob: parcela={} sumiu antes do lock", parcelaId);
            return;
        }
        if (atual.getStatus() != StatusParcela.ATRASADA) {
            // Concorrencia: outra instancia ja marcou — idempotente.
            return;
        }
        atual.marcarInadimplente();
        parcelaRepository.save(atual);
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
    }
}
