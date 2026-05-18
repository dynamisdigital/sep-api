package com.dynamis.sep_api.credito.application.service.dto;

import com.dynamis.sep_api.credito.domain.vo.StatusProposta;

import java.util.List;

/**
 * Output agregado do {@code MotorRegrasCredito.avaliar(...)} (Task 8.2). Contem score (0-1000),
 * status sugerido segundo thresholds configuraveis e a lista completa de regras avaliadas pra
 * persistencia em {@code RegraCreditoAvaliada}.
 */
public record ResultadoAvaliacaoCredito(
        int score, StatusProposta statusSugerido, int falhas, int pendencias, List<RegraResultado> regras) {

    public boolean temBloqueioAbsoluto() {
        return regras.stream()
                .anyMatch(r -> r.bloqueante() && r.resultado().name().equals("FALHOU"));
    }
}
