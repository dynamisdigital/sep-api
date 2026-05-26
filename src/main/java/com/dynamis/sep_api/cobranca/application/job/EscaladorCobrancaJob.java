package com.dynamis.sep_api.cobranca.application.job;

import com.dynamis.sep_api.cobranca.application.dto.EscalarCobrancaCommand;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.usecase.EscalarCobrancaUseCase;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
 * Job diario que reavalia parcelas em atraso e dispara a etapa do workflow correspondente (Sprint
 * 13 Task 13.4).
 *
 * <p>Roda 30 min depois do {@code MarcarParcelaAtrasadaJob} (Sprint 12) para garantir que
 * parcelas que viraram ATRASADA hoje ja estao persistidas; o listener de
 * {@code ParcelaAtrasouEvent} cobre o dia 0 — este job cobre os dias seguintes ({@code dia=5},
 * {@code dia=15}, etc.). Idempotencia eh garantida pela unique parcial
 * {@code uq_evento_notificacao_idempotencia} + checagem em memoria.
 *
 * <p>Restrito ao modulo via {@code @ConditionalOnProperty} pra permitir desligar em ITs alheios
 * via {@code application-test.yml}.
 */
@Component
@ConditionalOnProperty(name = "app.cobranca.scheduling-habilitado", havingValue = "true", matchIfMissing = true)
public class EscaladorCobrancaJob {

    private static final Logger log = LoggerFactory.getLogger(EscaladorCobrancaJob.class);
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("pt", "BR"));

    private final ParcelaCobrancaRepository parcelaRepository;
    private final ContratoCobrancaQueryPort contratoQuery;
    private final UsuarioRepository usuarioRepository;
    private final EscalarCobrancaUseCase useCase;
    private final Clock clock;

    public EscaladorCobrancaJob(
            ParcelaCobrancaRepository parcelaRepository,
            ContratoCobrancaQueryPort contratoQuery,
            UsuarioRepository usuarioRepository,
            EscalarCobrancaUseCase useCase,
            Clock clock) {
        this.parcelaRepository = parcelaRepository;
        this.contratoQuery = contratoQuery;
        this.usuarioRepository = usuarioRepository;
        this.useCase = useCase;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.cobranca.escalador-cron:0 0 3 * * *}", zone = "America/Sao_Paulo")
    public void escalarDiariamente() {
        executar();
    }

    /**
     * Visivel pra testes — controla {@code Clock} e chama diretamente sem agendamento.
     *
     * <p>{@code @Transactional} abre sessao Hibernate pro loop conseguir resolver lazy load de
     * {@code ParcelaCobranca.agenda}. Cada {@code escalarCobrancaUseCase.escalar} interno tem
     * tx propria — falha numa parcela nao reverte as anteriores.
     */
    @org.springframework.transaction.annotation.Transactional
    public int executar() {
        LocalDate hoje = LocalDate.now(clock);
        List<ParcelaCobranca> atrasadas =
                parcelaRepository.findByStatusAndDataVencimentoBefore(StatusParcela.ATRASADA, hoje.plusDays(1));
        int processadas = 0;
        for (ParcelaCobranca parcela : atrasadas) {
            int dias = (int) ChronoUnit.DAYS.between(parcela.getDataVencimento(), hoje);
            if (dias <= 0) {
                continue;
            }
            try {
                processarParcela(parcela, dias);
                processadas++;
            } catch (RuntimeException e) {
                log.warn("EscaladorCobrancaJob: falha ao escalar parcela={} dias={}", parcela.getId(), dias, e);
            }
        }
        if (processadas > 0) {
            log.info("EscaladorCobrancaJob: {} parcelas escaladas em {}", processadas, hoje);
        }
        return processadas;
    }

    private void processarParcela(ParcelaCobranca parcela, int dias) {
        UUID contratoId = parcela.getAgenda().getContratoId();
        Optional<UUID> tomadorIdOpt = contratoQuery.tomadorIdDoContrato(contratoId);
        if (tomadorIdOpt.isEmpty()) {
            log.warn("EscaladorCobrancaJob: sem tomadorId para contrato={} parcela={}", contratoId, parcela.getId());
            return;
        }
        // PRD §RF-01: Usuario.username eh o email validado (sem campo email separado).
        String email = usuarioRepository
                .findById(tomadorIdOpt.get())
                .map(Usuario::getUsername)
                .orElse(null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("numeroParcela", parcela.getNumero());
        vars.put("diasAtraso", dias);
        vars.put("dataVencimento", parcela.getDataVencimento().format(DATA_BR));
        vars.put("valor", "R$ " + parcela.valorTotal());
        useCase.escalar(new EscalarCobrancaCommand(parcela.getId(), dias, email, null, vars, null));
    }
}
