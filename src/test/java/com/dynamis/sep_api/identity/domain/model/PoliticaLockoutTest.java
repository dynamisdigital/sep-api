package com.dynamis.sep_api.identity.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fronteiras da politica de lockout (Sprint 33). Teste puro: sem banco, sem mock e sem relogio real
 * — {@code agora} e sempre explicito.
 */
class PoliticaLockoutTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-07-29T12:00:00-03:00");

    private final PoliticaLockout politica = new PoliticaLockout(5, Duration.ofMinutes(15), Duration.ofMinutes(30));

    /** Falhas a cada {@code intervalo}, terminando em {@code maisRecente} (ordem decrescente). */
    private static List<OffsetDateTime> falhas(int quantidade, OffsetDateTime maisRecente, Duration intervalo) {
        return IntStream.range(0, quantidade)
                .mapToObj(i -> maisRecente.minus(intervalo.multipliedBy(i)))
                .toList();
    }

    @Test
    void semFalhasNaoBloqueia() {
        assertThat(politica.eventoDeBloqueio(List.of(), AGORA)).isEmpty();
    }

    @Test
    void abaixoDoLimiteNaoBloqueia() {
        List<OffsetDateTime> quatroEmCincoMinutos = falhas(4, AGORA, Duration.ofMinutes(1));

        assertThat(politica.eventoDeBloqueio(quatroEmCincoMinutos, AGORA)).isEmpty();
    }

    @Test
    void cincoFalhasDentroDaJanelaBloqueiam() {
        List<OffsetDateTime> cincoEmDezMinutos = falhas(5, AGORA, Duration.ofMinutes(2));

        assertThat(politica.eventoDeBloqueio(cincoEmDezMinutos, AGORA)).contains(AGORA);
    }

    @Test
    void cincoFalhasExatamenteNaJanelaBloqueiam() {
        List<OffsetDateTime> cincoEmQuinzeMinutos =
                falhas(5, AGORA, Duration.ofMinutes(15).dividedBy(4));

        assertThat(politica.eventoDeBloqueio(cincoEmQuinzeMinutos, AGORA)).contains(AGORA);
    }

    /**
     * Regressao da Sprint 33: a implementacao anterior contava falhas na janela de 30 min, entao
     * este caso bloqueava mesmo nunca tendo havido 5 falhas em 15 minutos.
     */
    @Test
    void cincoFalhasEspalhadasAlemDaJanelaNaoBloqueiam() {
        List<OffsetDateTime> cincoEmVinteMinutos = falhas(5, AGORA, Duration.ofMinutes(5));

        assertThat(politica.eventoDeBloqueio(cincoEmVinteMinutos, AGORA)).isEmpty();
    }

    @Test
    void bloqueioSegueValendoAntesDeExpirar() {
        OffsetDateTime evento = AGORA.minusMinutes(29);
        List<OffsetDateTime> cincoEmDezMinutos = falhas(5, evento, Duration.ofMinutes(2));

        assertThat(politica.eventoDeBloqueio(cincoEmDezMinutos, AGORA)).contains(evento);
    }

    @Test
    void bloqueioExpiraTrintaMinutosAposOEvento() {
        OffsetDateTime evento = AGORA.minusMinutes(30);
        List<OffsetDateTime> cincoEmDezMinutos = falhas(5, evento, Duration.ofMinutes(2));

        assertThat(politica.eventoDeBloqueio(cincoEmDezMinutos, AGORA)).isEmpty();
    }

    /**
     * O desbloqueio conta do evento, nao do envelhecimento das falhas: aqui as 5 falhas ainda estao
     * dentro dos 30 min de leitura, mas o evento ja expirou.
     */
    @Test
    void bloqueioExpiradoNaoRevivePorFalhasAindaRecentes() {
        OffsetDateTime evento = AGORA.minusMinutes(31);
        List<OffsetDateTime> cincoEmDezMinutos = falhas(5, evento, Duration.ofMinutes(2));

        assertThat(politica.eventoDeBloqueio(cincoEmDezMinutos, AGORA)).isEmpty();
    }

    /** Uma falha recente isolada nao herda o bloqueio de falhas antigas que nunca formaram janela. */
    @Test
    void falhaRecenteIsoladaNaoBloqueiaComHistoricoDisperso() {
        List<OffsetDateTime> historico = new ArrayList<>();
        historico.add(AGORA);
        historico.addAll(falhas(4, AGORA.minusMinutes(20), Duration.ofMinutes(5)));

        assertThat(politica.eventoDeBloqueio(historico, AGORA)).isEmpty();
    }

    /**
     * A varredura nao para na falha mais recente: aqui ela sozinha nao fecha janela, mas o cluster
     * anterior fecha — e o evento devolvido e o do cluster, nao a falha recente.
     */
    @Test
    void continuaVarrendoQuandoAFalhaMaisRecenteNaoFechaJanela() {
        OffsetDateTime clusterMaisRecente = AGORA.minusMinutes(20);
        List<OffsetDateTime> historico = new ArrayList<>();
        historico.add(AGORA);
        historico.addAll(falhas(5, clusterMaisRecente, Duration.ofMinutes(1)));

        assertThat(politica.eventoDeBloqueio(historico, AGORA)).contains(clusterMaisRecente);
    }

    @Test
    void janelaDeLeituraCobreBloqueioMaisDeteccao() {
        assertThat(politica.janelaDeLeitura()).isEqualTo(Duration.ofMinutes(45));
    }

    /**
     * O limite de leitura sai da configuracao, nao de um numero fixo (Sprint 34 Task 34.1). Um teto
     * constante so seria seguro sob premissas sobre o que e gravado; a mesma politica com bloqueio
     * mais longo precisa ler mais historico.
     */
    @Test
    void limiteDeLeituraAcompanhaAConfiguracao() {
        PoliticaLockout bloqueioLongo = new PoliticaLockout(5, Duration.ofMinutes(15), Duration.ofMinutes(120));
        PoliticaLockout maisTentativas = new PoliticaLockout(9, Duration.ofMinutes(15), Duration.ofMinutes(30));

        assertThat(bloqueioLongo.limiteDeLeitura()).isGreaterThan(politica.limiteDeLeitura());
        assertThat(maisTentativas.limiteDeLeitura()).isGreaterThan(politica.limiteDeLeitura());
    }

    /**
     * A propriedade que o limite precisa garantir: truncar a leitura nao pode esconder um bloqueio.
     *
     * <p>Constroi o historico <b>mais denso possivel</b> que ainda nao fecha nenhuma janela — grupos
     * de {@code maxAttempts - 1} falhas simultaneas, separados por pouco mais que a janela de
     * deteccao — e confirma que ele nao cabe no limite. Consequencia: qualquer historico grande o
     * bastante para encher a leitura fecha alguma janela antes do corte.
     */
    @Test
    void limiteDeLeituraNaoCabeEmHistoricoSemJanelaFechada() {
        List<OffsetDateTime> semBloqueio = new ArrayList<>();
        OffsetDateTime inicioDaLeitura = AGORA.minus(politica.janelaDeLeitura());
        for (OffsetDateTime grupo = AGORA;
                grupo.isAfter(inicioDaLeitura);
                grupo = grupo.minus(politica.janelaDeteccao()).minusSeconds(1)) {
            for (int i = 0; i < politica.maxAttempts() - 1; i++) {
                semBloqueio.add(grupo);
            }
        }

        assertThat(politica.eventoDeBloqueio(semBloqueio, AGORA))
                .as("historico construido de proposito para nao fechar janela")
                .isEmpty();
        assertThat(semBloqueio).hasSizeLessThan(politica.limiteDeLeitura());
    }

    @Test
    void limiteInvalidoEhRejeitado() {
        assertThatThrownBy(() -> new PoliticaLockout(0, Duration.ofMinutes(15), Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Configuracao negativa desligaria o lockout em silencio: duracao negativa joga o limite de
     * validade no futuro e janela negativa nunca fecha. Falhar no boot e melhor que fail-open mudo.
     */
    @Test
    void duracoesNegativasSaoRejeitadas() {
        assertThatThrownBy(() -> new PoliticaLockout(5, Duration.ofMinutes(-1), Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaLockout(5, Duration.ofMinutes(15), Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
