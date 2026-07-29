package com.dynamis.sep_api.identity.domain.model;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Politica de account lockout (Sprint 5 Task 5.4; conformidade na Sprint 33).
 *
 * <p>Regra: {@code maxAttempts} falhas dentro de {@code janelaDeteccao} bloqueiam a conta por
 * {@code duracaoBloqueio}, contados a partir da falha mais recente que fecha uma janela.
 *
 * <p>Decisao pura de dominio: recebe os instantes das falhas e o instante atual, sem repositorio,
 * relogio ou configuracao. Isso permite testar as fronteiras de 15 e de 30 minutos sem banco.
 */
public record PoliticaLockout(int maxAttempts, Duration janelaDeteccao, Duration duracaoBloqueio) {

    /**
     * Rejeita configuracao que desligaria o lockout em silencio: com duracao negativa o limite de
     * validade cai no futuro e nada bloqueia; com janela negativa nenhuma janela fecha. Um lockout
     * fail-open sem log e pior que um boot que falha.
     */
    public PoliticaLockout {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts deve ser >= 1");
        }
        if (janelaDeteccao == null || janelaDeteccao.isNegative()) {
            throw new IllegalArgumentException("janelaDeteccao deve ser >= 0");
        }
        if (duracaoBloqueio == null || duracaoBloqueio.isNegative()) {
            throw new IllegalArgumentException("duracaoBloqueio deve ser >= 0");
        }
    }

    /**
     * Quanto de historico precisa ser lido para decidir. O evento de bloqueio candidato mais antigo
     * ainda valido esta em {@code agora - duracaoBloqueio}, e a deteccao dele olha
     * {@code janelaDeteccao} para tras.
     */
    public Duration janelaDeLeitura() {
        return duracaoBloqueio.plus(janelaDeteccao);
    }

    /**
     * Instante da falha mais recente que fecha uma janela e cujo bloqueio ainda vale em
     * {@code agora}; vazio se a conta estiver liberada.
     *
     * <p>Haver {@code maxAttempts} falhas em {@code [t - janelaDeteccao, t]} equivale a as
     * {@code maxAttempts} falhas mais recentes ate {@code t} caberem na janela — e por isso que
     * basta comparar {@code t} com a {@code maxAttempts}-esima falha anterior a ele.
     *
     * @param falhasMaisRecentesPrimeiro instantes das falhas em ordem <b>decrescente</b>. A ordem e
     *     pre-requisito: a varredura para no primeiro candidato fora do bloqueio assumindo que os
     *     seguintes sao mais antigos.
     */
    public Optional<OffsetDateTime> eventoDeBloqueio(
            List<OffsetDateTime> falhasMaisRecentesPrimeiro, OffsetDateTime agora) {
        OffsetDateTime limiteDeValidade = agora.minus(duracaoBloqueio);
        for (int i = 0; i + maxAttempts - 1 < falhasMaisRecentesPrimeiro.size(); i++) {
            OffsetDateTime candidato = falhasMaisRecentesPrimeiro.get(i);
            if (!candidato.isAfter(limiteDeValidade)) {
                return Optional.empty();
            }
            OffsetDateTime maisAntigaDaJanela = falhasMaisRecentesPrimeiro.get(i + maxAttempts - 1);
            if (Duration.between(maisAntigaDaJanela, candidato).compareTo(janelaDeteccao) <= 0) {
                return Optional.of(candidato);
            }
        }
        return Optional.empty();
    }
}
