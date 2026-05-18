package com.dynamis.sep_api.credito.application.usecase;

import com.dynamis.sep_api.credito.application.service.PropostaAvaliacaoTransacional;
import com.dynamis.sep_api.credito.application.service.dto.ResultadoAvaliacaoCredito;
import com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Orquestrador da avaliacao automatica de proposta (Sprint 8 Task 8.3). Delega o happy path e o
 * fallback de PENDENCIA ao {@link PropostaAvaliacaoTransacional}, que abre transacao propria pra
 * cada operacao ({@code REQUIRES_NEW}).
 *
 * <p>{@code executar} NAO eh transacional na entry — qualquer falha do happy path (onboarding
 * indisponivel, motor lanca, persistencia falha) eh capturada e dispara o fallback isolado. Sem
 * isso, a transacao do happy path poderia estar {@code rollback-only} no momento do catch e o
 * save da PENDENCIA seria perdido silenciosamente.
 *
 * <p>{@link PropostaNaoEncontradaException} eh propagada (404) — proposta sequer existe; nao faz
 * sentido tentar marcar PENDENCIA nesse caso.
 */
@Service
public class AvaliarPropostaUseCase {

    private static final Logger log = LoggerFactory.getLogger(AvaliarPropostaUseCase.class);

    private final PropostaAvaliacaoTransacional transacional;

    public AvaliarPropostaUseCase(PropostaAvaliacaoTransacional transacional) {
        this.transacional = transacional;
    }

    public ResultadoAvaliacaoCredito executar(UUID propostaId) {
        try {
            return transacional.avaliar(propostaId);
        } catch (PropostaNaoEncontradaException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn(
                    "Falha ao avaliar proposta {}; movendo para PENDENCIA. Motivo: {}",
                    propostaId,
                    ex.getMessage(),
                    ex);
            try {
                transacional.moverParaPendencia(propostaId);
            } catch (RuntimeException pendenciaEx) {
                log.error(
                        "Falha tambem ao marcar PENDENCIA para proposta {}: {}",
                        propostaId,
                        pendenciaEx.getMessage(),
                        pendenciaEx);
            }
            return new ResultadoAvaliacaoCredito(0, StatusProposta.PENDENCIA, 0, 0, List.of());
        }
    }
}
