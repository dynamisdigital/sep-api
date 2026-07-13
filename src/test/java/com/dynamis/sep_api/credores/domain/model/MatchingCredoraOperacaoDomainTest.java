package com.dynamis.sep_api.credores.domain.model;

import com.dynamis.sep_api.credores.domain.vo.CriterioMatchingCredoraOperacao;
import com.dynamis.sep_api.credores.domain.vo.StatusMatchingCredoraOperacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Transicoes de estado da sugestao de matching (Sprint 30 Task 30.2): nasce {@code SUGERIDA},
 * decisao assistida leva a {@code CONFIRMADA} ou {@code REJEITADA} (terminais), replay em terminal
 * falha sem alterar estado e sem vazar identificadores.
 */
class MatchingCredoraOperacaoDomainTest {

    private static final List<CriterioMatchingCredoraOperacao> CRITERIOS = List.of(
            CriterioMatchingCredoraOperacao.CREDORA_ATIVA,
            CriterioMatchingCredoraOperacao.CREDORA_ELEGIVEL,
            CriterioMatchingCredoraOperacao.OPERACAO_ATIVA,
            CriterioMatchingCredoraOperacao.CONTRATO_ASSINADO,
            CriterioMatchingCredoraOperacao.VALOR_OPERACAO_DISPONIVEL,
            CriterioMatchingCredoraOperacao.PAR_SEM_MATCHING_PREVIO);

    private static MatchingCredoraOperacao novaSugestao() {
        return MatchingCredoraOperacao.sugerir(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10000.5"), CRITERIOS);
    }

    @Test
    void sugerirNasceSugeridaComSnapshotDeterministicoEValorNormalizado() {
        MatchingCredoraOperacao matching = novaSugestao();

        assertThat(matching.getId()).isNotNull();
        assertThat(matching.getStatus()).isEqualTo(StatusMatchingCredoraOperacao.SUGERIDA);
        assertThat(matching.getValorElegivel()).isEqualByComparingTo("10000.50");
        assertThat(matching.getValorElegivel().scale()).isEqualTo(2);
        assertThat(matching.getCriteriosSnapshot())
                .isEqualTo("CREDORA_ATIVA;CREDORA_ELEGIVEL;OPERACAO_ATIVA;CONTRATO_ASSINADO;"
                        + "VALOR_OPERACAO_DISPONIVEL;PAR_SEM_MATCHING_PREVIO");
        assertThat(matching.getDecididoPorUsuarioId()).isNull();
        assertThat(matching.getMotivoDecisaoSanitizado()).isNull();
        assertThat(matching.getDataDecisao()).isNull();
    }

    @Test
    void sugerirValidaObrigatorios() {
        UUID credoraId = UUID.randomUUID();
        UUID operacaoId = UUID.randomUUID();

        assertThatNullPointerException()
                .isThrownBy(() -> MatchingCredoraOperacao.sugerir(null, operacaoId, BigDecimal.TEN, CRITERIOS));
        assertThatNullPointerException()
                .isThrownBy(() -> MatchingCredoraOperacao.sugerir(credoraId, null, BigDecimal.TEN, CRITERIOS));
        assertThatNullPointerException()
                .isThrownBy(() -> MatchingCredoraOperacao.sugerir(credoraId, operacaoId, null, CRITERIOS));
        assertThatNullPointerException()
                .isThrownBy(() -> MatchingCredoraOperacao.sugerir(credoraId, operacaoId, BigDecimal.TEN, null));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> MatchingCredoraOperacao.sugerir(credoraId, operacaoId, BigDecimal.ZERO, CRITERIOS));
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        MatchingCredoraOperacao.sugerir(credoraId, operacaoId, new BigDecimal("-1.00"), CRITERIOS));
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        MatchingCredoraOperacao.sugerir(credoraId, operacaoId, new BigDecimal("10.123"), CRITERIOS));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MatchingCredoraOperacao.sugerir(credoraId, operacaoId, BigDecimal.TEN, List.of()));
    }

    @Test
    void confirmarRegistraAtorDataEMotivoSanitizado() {
        MatchingCredoraOperacao matching = novaSugestao();
        UUID operadorId = UUID.randomUUID();
        OffsetDateTime antes = OffsetDateTime.now();

        matching.confirmar(operadorId, "  aderente a carteira  ");

        assertThat(matching.getStatus()).isEqualTo(StatusMatchingCredoraOperacao.CONFIRMADA);
        assertThat(matching.getDecididoPorUsuarioId()).isEqualTo(operadorId);
        assertThat(matching.getMotivoDecisaoSanitizado()).isEqualTo("aderente a carteira");
        assertThat(matching.getDataDecisao()).isNotNull().isAfterOrEqualTo(antes);
    }

    @Test
    void rejeitarRegistraAtorDataEMotivoSanitizado() {
        MatchingCredoraOperacao matching = novaSugestao();
        UUID operadorId = UUID.randomUUID();

        matching.rejeitar(operadorId, "fora do apetite");

        assertThat(matching.getStatus()).isEqualTo(StatusMatchingCredoraOperacao.REJEITADA);
        assertThat(matching.getDecididoPorUsuarioId()).isEqualTo(operadorId);
        assertThat(matching.getMotivoDecisaoSanitizado()).isEqualTo("fora do apetite");
        assertThat(matching.getDataDecisao()).isNotNull();
    }

    @Test
    void decisaoSemMotivoNormalizaParaNulo() {
        MatchingCredoraOperacao confirmado = novaSugestao();
        confirmado.confirmar(UUID.randomUUID(), null);
        assertThat(confirmado.getMotivoDecisaoSanitizado()).isNull();

        MatchingCredoraOperacao rejeitado = novaSugestao();
        rejeitado.rejeitar(UUID.randomUUID(), "   ");
        assertThat(rejeitado.getMotivoDecisaoSanitizado()).isNull();
    }

    @Test
    void decisaoExigeAtor() {
        assertThatNullPointerException().isThrownBy(() -> novaSugestao().confirmar(null, "motivo"));
        assertThatNullPointerException().isThrownBy(() -> novaSugestao().rejeitar(null, "motivo"));
    }

    @Test
    void motivoAcimaDoLimiteFalha() {
        String motivoLongo = "x".repeat(256);
        assertThatIllegalArgumentException().isThrownBy(() -> novaSugestao().confirmar(UUID.randomUUID(), motivoLongo));
    }

    @Test
    void transicaoAPartirDeTerminalFalhaSemAlterarEstado() {
        MatchingCredoraOperacao confirmado = novaSugestao();
        UUID decisorOriginal = UUID.randomUUID();
        confirmado.confirmar(decisorOriginal, "ok");
        OffsetDateTime dataDecisaoOriginal = confirmado.getDataDecisao();

        assertThatIllegalStateException().isThrownBy(() -> confirmado.confirmar(UUID.randomUUID(), "replay"));
        assertThatIllegalStateException().isThrownBy(() -> confirmado.rejeitar(UUID.randomUUID(), "replay"));
        assertThat(confirmado.getStatus()).isEqualTo(StatusMatchingCredoraOperacao.CONFIRMADA);
        assertThat(confirmado.getDecididoPorUsuarioId()).isEqualTo(decisorOriginal);
        assertThat(confirmado.getMotivoDecisaoSanitizado()).isEqualTo("ok");
        assertThat(confirmado.getDataDecisao()).isEqualTo(dataDecisaoOriginal);

        MatchingCredoraOperacao rejeitado = novaSugestao();
        rejeitado.rejeitar(UUID.randomUUID(), null);
        assertThatIllegalStateException().isThrownBy(() -> rejeitado.confirmar(UUID.randomUUID(), null));
        assertThat(rejeitado.getStatus()).isEqualTo(StatusMatchingCredoraOperacao.REJEITADA);
    }

    @Test
    void statusTerminalSomenteAposDecisao() {
        assertThat(StatusMatchingCredoraOperacao.SUGERIDA.terminal()).isFalse();
        assertThat(StatusMatchingCredoraOperacao.CONFIRMADA.terminal()).isTrue();
        assertThat(StatusMatchingCredoraOperacao.REJEITADA.terminal()).isTrue();
    }

    @Test
    void excecaoDeTransicaoNaoVazaIdentificadoresNemMotivo() {
        MatchingCredoraOperacao matching = novaSugestao();
        matching.confirmar(UUID.randomUUID(), "motivo confidencial");

        Throwable erro = catchThrowable(() -> matching.rejeitar(UUID.randomUUID(), "outro"));

        assertThat(erro).isInstanceOf(IllegalStateException.class);
        assertThat(erro.getMessage())
                .contains("CONFIRMADA")
                .doesNotContain(matching.getId().toString())
                .doesNotContain(matching.getEmpresaCredoraId().toString())
                .doesNotContain(matching.getOperacaoId().toString())
                .doesNotContain("confidencial");
    }

    @Test
    void toStringNaoVazaIdentificadoresNemMotivo() {
        MatchingCredoraOperacao matching = novaSugestao();
        matching.confirmar(UUID.randomUUID(), "motivo confidencial");

        String texto = matching.toString();

        assertThat(texto)
                .doesNotContain(matching.getEmpresaCredoraId().toString())
                .doesNotContain(matching.getOperacaoId().toString())
                .doesNotContain("confidencial");
    }
}
