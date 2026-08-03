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
     * Quantas falhas basta ler, mais recentes primeiro, para que a decisao nao dependa de
     * truncamento. Derivado da propria politica: um teto fixo so seria seguro sob premissas sobre o
     * que e gravado, e premissa nao e invariante.
     *
     * <p>O raciocinio: truncar em {@code N} so poderia esconder um bloqueio se nenhuma das
     * {@code N} falhas lidas fechasse uma janela e alguma mais antiga fechasse. Se nenhuma fecha,
     * entao para todo indice {@code i} vale {@code F[i] - F[i + maxAttempts - 1] > janelaDeteccao}.
     * Encadeando {@code m} saltos de {@code maxAttempts - 1} posicoes (eles compartilham extremo, e
     * por isso o passo e {@code maxAttempts - 1} e nao {@code maxAttempts}), as falhas percorridas
     * gastam mais que {@code m * janelaDeteccao}. Como todas cabem em {@link #janelaDeLeitura()},
     * existe um teto para {@code m}, e portanto {@code N <= (m + 1) * (maxAttempts - 1)}. O
     * {@code + 1} final torna a pagina estritamente maior que o pior historico possivel.
     *
     * <p>Divisor minimo de um segundo: com {@code janelaDeteccao} zero o encadeamento nao gasta
     * tempo e nao ha teto derivavel. A configuracao e degenerada (so falhas simultaneas bloqueiam) e
     * a resposta certa e ler <b>mais</b>, nao menos.
     *
     * <p>Satura em vez de estourar: uma configuracao absurda deve produzir uma leitura grande, nao
     * uma excecao — que aqui cairia dentro do login e trocaria erro de config por indisponibilidade
     * de autenticacao.
     */
    public int limiteDeLeitura() {
        long janelasNaLeitura = janelaDeLeitura().toSeconds() / Math.max(1L, janelaDeteccao.toSeconds());
        long grupos = Math.min(janelasNaLeitura + 1L, Integer.MAX_VALUE);
        return (int) Math.min(grupos * (maxAttempts - 1L) + 1L, Integer.MAX_VALUE);
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
