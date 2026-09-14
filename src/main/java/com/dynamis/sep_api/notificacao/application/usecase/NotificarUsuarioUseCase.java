package com.dynamis.sep_api.notificacao.application.usecase;

import com.dynamis.sep_api.notificacao.application.port.out.EnvioEmailPort;
import com.dynamis.sep_api.notificacao.application.port.out.NotificacaoPort;
import com.dynamis.sep_api.notificacao.application.port.out.dto.EmailNotificacao;
import com.dynamis.sep_api.notificacao.domain.model.Notificacao;
import com.dynamis.sep_api.notificacao.domain.vo.ConteudoNotificacao;
import com.dynamis.sep_api.notificacao.domain.vo.OrigemNotificacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entrega uma notificacao a um usuario pelo canal escolhido por quem chama (Sprint 38, ADR 0021).
 *
 * <p>Sem {@code @Transactional}: cada gravacao roda em transacao propria no {@link NotificacaoPort}.
 * No e-mail isso separa tres momentos — {@code PENDENTE} comitado, envio fora de transacao,
 * resultado comitado —, e uma queda entre o envio e o resultado deixa a linha {@code PENDENTE}, que a
 * chave de origem impede de reenviar (ADR 0021 §6).
 *
 * <p>Falha do provider vira {@code FALHOU} e nao sobe. Falha de persistencia sobe: o listener que
 * chama decide o que registrar.
 */
@Service
public class NotificarUsuarioUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotificarUsuarioUseCase.class);
    private static final int TAMANHO_MAXIMO_MOTIVO = 200;

    private final NotificacaoPort notificacaoPort;
    private final EnvioEmailPort envioEmailPort;
    private final Clock clock;

    public NotificarUsuarioUseCase(NotificacaoPort notificacaoPort, EnvioEmailPort envioEmailPort, Clock clock) {
        this.notificacaoPort = notificacaoPort;
        this.envioEmailPort = envioEmailPort;
        this.clock = clock;
    }

    /** {@code IN_APP}: gravar e a entrega. Nenhum I/O externo. */
    public void disponibilizarNaCentral(UUID usuarioId, OrigemNotificacao origem, ConteudoNotificacao conteudo) {
        Notificacao notificacao = Notificacao.disponibilizarInApp(usuarioId, origem, conteudo, agora());
        if (!notificacaoPort.registrarSeInedita(notificacao)) {
            registrarDuplicata(origem, "in_app");
        }
    }

    /**
     * {@code EMAIL}: registra a tentativa antes de enviar e grava o resultado depois. O endereco so
     * existe durante o envio.
     */
    public void enviarEmail(
            UUID usuarioId, String enderecoEmail, OrigemNotificacao origem, ConteudoNotificacao conteudo) {
        EmailNotificacao email = new EmailNotificacao(enderecoEmail, conteudo.titulo(), conteudo.mensagem());
        Notificacao notificacao = Notificacao.registrarEmail(usuarioId, origem, conteudo, agora());
        if (!notificacaoPort.registrarSeInedita(notificacao)) {
            registrarDuplicata(origem, "email");
            return;
        }
        enviar(email, notificacao);
        notificacaoPort.atualizarEntrega(notificacao);
    }

    private void enviar(EmailNotificacao email, Notificacao notificacao) {
        try {
            switch (envioEmailPort.enviar(email)) {
                case ENVIADO -> notificacao.confirmarEnvio(agora());
                case SIMULADO -> notificacao.registrarSimulacao(agora());
            }
        } catch (RuntimeException falha) {
            String motivo = motivoSanitizado(falha);
            notificacao.registrarFalha(motivo, agora());
            log.atWarn()
                    .addKeyValue("event", "notification_failed")
                    .addKeyValue("channel", "email")
                    .addKeyValue("tipo", notificacao.getOrigem().tipo())
                    .addKeyValue("motivo", motivo)
                    .log("Envio de email falhou");
        }
    }

    /**
     * So o nome da classe: a mensagem de excecao de provider costuma carregar endereco, host ou trecho
     * do corpo (ADR 0021 §5).
     */
    private static String motivoSanitizado(RuntimeException falha) {
        String nome = falha.getClass().getName();
        return nome.length() > TAMANHO_MAXIMO_MOTIVO ? nome.substring(0, TAMANHO_MAXIMO_MOTIVO) : nome;
    }

    private static void registrarDuplicata(OrigemNotificacao origem, String canal) {
        log.atInfo()
                .addKeyValue("event", "notification_duplicate")
                .addKeyValue("channel", canal)
                .addKeyValue("tipo", origem.tipo())
                .log("Notificacao ja registrada para esta origem");
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(clock);
    }
}
