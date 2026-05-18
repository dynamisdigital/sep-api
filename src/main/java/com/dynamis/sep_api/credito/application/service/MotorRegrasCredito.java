package com.dynamis.sep_api.credito.application.service;

import com.dynamis.sep_api.credito.application.service.dto.ContextoAvaliacaoCredito;
import com.dynamis.sep_api.credito.application.service.dto.RegraResultado;
import com.dynamis.sep_api.credito.application.service.dto.ResultadoAvaliacaoCredito;
import com.dynamis.sep_api.credito.domain.vo.ResultadoRegra;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orquestrador do motor de regras de credito (Sprint 8 Task 8.2 — ADR 0011 motor Java puro).
 *
 * <p>Recebe a lista de {@link RegraCredito} via injecao (todas as {@code @Component}s que
 * implementam a interface sao agregadas automaticamente pelo Spring). Avalia cada regra contra o
 * contexto, calcula score agregado e sugere {@link StatusProposta} inicial conforme thresholds
 * configuraveis em {@link CreditoMotorProperties}.
 *
 * <p>Formula do score:
 *
 * <pre>
 *   score = max(0, scoreInicial - penalidadeFalha * falhas - penalidadePendencia * pendencias)
 * </pre>
 *
 * <p>Status sugerido:
 *
 * <ul>
 *   <li>Qualquer regra com {@code FALHOU + bloqueante} -> {@link StatusProposta#REJEITADA}
 *       (rejeicao absoluta, independente do score).
 *   <li>Score >= scorePreAprovacao -> {@link StatusProposta#PRE_APROVADA}.
 *   <li>Score >= scoreAnalise -> {@link StatusProposta#EM_ANALISE}.
 *   <li>Caso contrario -> {@link StatusProposta#REJEITADA}.
 * </ul>
 */
@Service
public class MotorRegrasCredito {

    private final List<RegraCredito> regras;
    private final CreditoMotorProperties properties;

    public MotorRegrasCredito(List<RegraCredito> regras, CreditoMotorProperties properties) {
        this.regras = regras;
        this.properties = properties;
    }

    public ResultadoAvaliacaoCredito avaliar(ContextoAvaliacaoCredito contexto) {
        List<RegraResultado> resultados = new ArrayList<>(regras.size());
        int falhas = 0;
        int pendencias = 0;
        boolean bloqueioAbsoluto = false;

        for (RegraCredito regra : regras) {
            RegraResultado r = regra.avaliar(contexto);
            resultados.add(r);
            if (r.resultado() == ResultadoRegra.FALHOU) {
                falhas++;
                if (r.bloqueante()) {
                    bloqueioAbsoluto = true;
                }
            } else if (r.resultado() == ResultadoRegra.PENDENTE) {
                pendencias++;
            }
        }

        int score = calcularScore(falhas, pendencias);
        StatusProposta sugerido = sugerirStatus(score, bloqueioAbsoluto);
        return new ResultadoAvaliacaoCredito(
                score, sugerido, falhas, pendencias, Collections.unmodifiableList(resultados));
    }

    private int calcularScore(int falhas, int pendencias) {
        int penalidade = properties.penalidadeFalha() * falhas + properties.penalidadePendencia() * pendencias;
        int score = properties.scoreInicial() - penalidade;
        return Math.max(0, score);
    }

    private StatusProposta sugerirStatus(int score, boolean bloqueioAbsoluto) {
        if (bloqueioAbsoluto) {
            return StatusProposta.REJEITADA;
        }
        if (score >= properties.scorePreAprovacao()) {
            return StatusProposta.PRE_APROVADA;
        }
        if (score >= properties.scoreAnalise()) {
            return StatusProposta.EM_ANALISE;
        }
        return StatusProposta.REJEITADA;
    }
}
