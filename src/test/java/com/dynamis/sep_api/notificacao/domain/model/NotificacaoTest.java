package com.dynamis.sep_api.notificacao.domain.model;

import com.dynamis.sep_api.notificacao.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.ConteudoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.Entrega;
import com.dynamis.sep_api.notificacao.domain.vo.OrigemNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.SituacaoEntrega;
import com.dynamis.sep_api.notificacao.domain.vo.TipoNotificacao;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificacaoTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 9, 14, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID USUARIO = UUID.randomUUID();
    private static final OrigemNotificacao ORIGEM = new OrigemNotificacao(
            TipoNotificacao.DESEMBOLSO_PIX_CONCLUIDO, UUID.randomUUID().toString());
    private static final ConteudoNotificacao CONTEUDO = new ConteudoNotificacao("Titulo", "Mensagem", null);

    @Test
    void inApp_nasceDisponivelENaoLida() {
        Notificacao notificacao = Notificacao.disponibilizarInApp(USUARIO, ORIGEM, CONTEUDO, AGORA);

        assertThat(notificacao.getId()).isNotNull();
        assertThat(notificacao.getUsuarioId()).isEqualTo(USUARIO);
        assertThat(notificacao.getCriadaEm()).isEqualTo(AGORA);
        assertThat(notificacao.getEntrega()).isEqualTo(Entrega.inAppDisponivel(AGORA));
        assertThat(notificacao.getLidaEm()).isEmpty();
    }

    @Test
    void email_nascePendente() {
        Notificacao notificacao = Notificacao.registrarEmail(USUARIO, ORIGEM, CONTEUDO, AGORA);

        assertThat(notificacao.getEntrega().canal()).isEqualTo(CanalNotificacao.EMAIL);
        assertThat(notificacao.getEntrega().situacao()).isEqualTo(SituacaoEntrega.PENDENTE);
    }

    @Test
    void cadaNotificacaoNovaTemIdProprio() {
        assertThat(Notificacao.disponibilizarInApp(USUARIO, ORIGEM, CONTEUDO, AGORA)
                        .getId())
                .isNotEqualTo(Notificacao.disponibilizarInApp(USUARIO, ORIGEM, CONTEUDO, AGORA)
                        .getId());
    }

    @Test
    void marcarLida_eIdempotenteEPreservaAPrimeiraLeitura() {
        Notificacao notificacao = Notificacao.disponibilizarInApp(USUARIO, ORIGEM, CONTEUDO, AGORA);

        assertThat(notificacao.marcarLida(AGORA.plusMinutes(1))).isTrue();
        assertThat(notificacao.marcarLida(AGORA.plusMinutes(9))).isFalse();

        assertThat(notificacao.getLidaEm()).contains(AGORA.plusMinutes(1));
    }

    @Test
    void marcarLida_emEmail_eRecusado() {
        Notificacao email = Notificacao.registrarEmail(USUARIO, ORIGEM, CONTEUDO, AGORA);

        assertThatThrownBy(() -> email.marcarLida(AGORA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IN_APP");
        assertThat(email.getLidaEm()).isEmpty();
    }

    @Test
    void reconstituir_emailComLeitura_eRecusado() {
        assertThatThrownBy(() -> Notificacao.reconstituir(
                        UUID.randomUUID(),
                        USUARIO,
                        ORIGEM,
                        CONTEUDO,
                        Entrega.emailPendente(AGORA),
                        AGORA,
                        AGORA.plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IN_APP");
    }

    @Test
    void reconstituir_preservaTodosOsCampos() {
        UUID id = UUID.randomUUID();
        Entrega entrega = Entrega.inAppDisponivel(AGORA);

        Notificacao notificacao =
                Notificacao.reconstituir(id, USUARIO, ORIGEM, CONTEUDO, entrega, AGORA, AGORA.plusHours(1));

        assertThat(notificacao.getId()).isEqualTo(id);
        assertThat(notificacao.getOrigem()).isEqualTo(ORIGEM);
        assertThat(notificacao.getConteudo()).isEqualTo(CONTEUDO);
        assertThat(notificacao.getEntrega()).isEqualTo(entrega);
        assertThat(notificacao.getLidaEm()).contains(AGORA.plusHours(1));
    }

    @Test
    void resultadoDeEnvio_eRegistradoNoEmailPendente() {
        Notificacao enviada = Notificacao.registrarEmail(USUARIO, ORIGEM, CONTEUDO, AGORA);
        Notificacao simulada = Notificacao.registrarEmail(USUARIO, ORIGEM, CONTEUDO, AGORA);
        Notificacao falhou = Notificacao.registrarEmail(USUARIO, ORIGEM, CONTEUDO, AGORA);

        enviada.confirmarEnvio(AGORA.plusSeconds(1));
        simulada.registrarSimulacao(AGORA.plusSeconds(2));
        falhou.registrarFalha("MailSendException", AGORA.plusSeconds(3));

        assertThat(enviada.getEntrega())
                .isEqualTo(new Entrega(CanalNotificacao.EMAIL, SituacaoEntrega.ENVIADA, AGORA.plusSeconds(1), null));
        assertThat(simulada.getEntrega())
                .isEqualTo(new Entrega(CanalNotificacao.EMAIL, SituacaoEntrega.SIMULADA, AGORA.plusSeconds(2), null));
        assertThat(falhou.getEntrega())
                .isEqualTo(new Entrega(
                        CanalNotificacao.EMAIL, SituacaoEntrega.FALHOU, AGORA.plusSeconds(3), "MailSendException"));
    }

    @Test
    void resultadoDeEnvio_soUmaVez() {
        Notificacao email = Notificacao.registrarEmail(USUARIO, ORIGEM, CONTEUDO, AGORA);
        email.registrarSimulacao(AGORA.plusSeconds(1));

        assertThatThrownBy(() -> email.registrarFalha("Timeout", AGORA.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(email.getEntrega().situacao()).isEqualTo(SituacaoEntrega.SIMULADA);
    }

    @Test
    void resultadoDeEnvio_emInApp_eRecusado() {
        Notificacao inApp = Notificacao.disponibilizarInApp(USUARIO, ORIGEM, CONTEUDO, AGORA);

        assertThatThrownBy(() -> inApp.confirmarEnvio(AGORA)).isInstanceOf(IllegalStateException.class);
        assertThat(inApp.getEntrega().situacao()).isEqualTo(SituacaoEntrega.DISPONIVEL);
    }
}
