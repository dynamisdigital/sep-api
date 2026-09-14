package com.dynamis.sep_api.notificacao.domain.vo;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrigemEConteudoNotificacaoTest {

    @Test
    void origem_exigeIdEntreUmECemCaracteres() {
        String noLimite = "o".repeat(OrigemNotificacao.TAMANHO_MAXIMO_ID);

        assertThat(new OrigemNotificacao(TipoNotificacao.CONTA_BLOQUEADA, noLimite).id())
                .isEqualTo(noLimite);
        assertThatThrownBy(() -> new OrigemNotificacao(TipoNotificacao.CONTA_BLOQUEADA, noLimite + "o"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrigemNotificacao(TipoNotificacao.CONTA_BLOQUEADA, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrigemNotificacao(null, "id")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void conteudo_exigeTituloEMensagemDentroDoLimite() {
        String titulo = "t".repeat(ConteudoNotificacao.TAMANHO_MAXIMO_TITULO);
        String mensagem = "m".repeat(ConteudoNotificacao.TAMANHO_MAXIMO_MENSAGEM);

        assertThat(new ConteudoNotificacao(titulo, mensagem, null).titulo()).isEqualTo(titulo);
        assertThatThrownBy(() -> new ConteudoNotificacao(titulo + "t", mensagem, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConteudoNotificacao(titulo, mensagem + "m", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConteudoNotificacao("", mensagem, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void referencia_exigeTipoEId() {
        assertThatThrownBy(() -> new Referencia(null, UUID.randomUUID())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Referencia(TipoReferencia.CONTRATO, null))
                .isInstanceOf(NullPointerException.class);
    }
}
