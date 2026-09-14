package com.dynamis.sep_api.notificacao.domain.vo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntregaTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 9, 14, 10, 0, 0, 0, ZoneOffset.UTC);

    @ParameterizedTest
    @EnumSource(value = SituacaoEntrega.class, names = "DISPONIVEL", mode = EnumSource.Mode.EXCLUDE)
    void inApp_soAdmiteDisponivel(SituacaoEntrega situacao) {
        String motivo = situacao == SituacaoEntrega.FALHOU ? "Motivo" : null;

        assertThatThrownBy(() -> new Entrega(CanalNotificacao.IN_APP, situacao, AGORA, motivo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incompativel");
    }

    @Test
    void email_naoAdmiteDisponivel() {
        assertThatThrownBy(() -> new Entrega(CanalNotificacao.EMAIL, SituacaoEntrega.DISPONIVEL, AGORA, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incompativel");
    }

    @ParameterizedTest
    @EnumSource(SituacaoEntrega.class)
    void sms_naoTemEntregaNesteModulo(SituacaoEntrega situacao) {
        String motivo = situacao == SituacaoEntrega.FALHOU ? "Motivo" : null;

        assertThatThrownBy(() -> new Entrega(CanalNotificacao.SMS, situacao, AGORA, motivo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incompativel");
    }

    @Test
    void motivoFalha_existeSeESomenteSeFalhou() {
        assertThatThrownBy(() -> new Entrega(CanalNotificacao.EMAIL, SituacaoEntrega.FALHOU, AGORA, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Entrega(CanalNotificacao.EMAIL, SituacaoEntrega.SIMULADA, AGORA, "Motivo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void motivoFalha_temLimiteDeTamanho() {
        String noLimite = "x".repeat(Entrega.TAMANHO_MAXIMO_MOTIVO);

        assertThat(Entrega.emailPendente(AGORA).aposFalha(noLimite, AGORA).motivoFalha())
                .isEqualTo(noLimite);
        assertThatThrownBy(() -> Entrega.emailPendente(AGORA).aposFalha(noLimite + "x", AGORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Entrega.emailPendente(AGORA).aposFalha(" ", AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultado_naoAlteraAEntregaOriginal() {
        Entrega pendente = Entrega.emailPendente(AGORA);

        Entrega enviada = pendente.aposEnvio(AGORA.plusSeconds(1));

        assertThat(pendente.situacao()).isEqualTo(SituacaoEntrega.PENDENTE);
        assertThat(enviada.situacao()).isEqualTo(SituacaoEntrega.ENVIADA);
        assertThat(enviada.atualizadaEm()).isEqualTo(AGORA.plusSeconds(1));
    }
}
