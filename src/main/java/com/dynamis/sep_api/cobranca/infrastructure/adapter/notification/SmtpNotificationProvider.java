package com.dynamis.sep_api.cobranca.infrastructure.adapter.notification;

import com.dynamis.sep_api.cobranca.application.port.out.NotificationProvider;
import com.dynamis.sep_api.cobranca.application.port.out.TemplateNotificacaoEngine;
import com.dynamis.sep_api.cobranca.application.port.out.dto.Notificacao;
import com.dynamis.sep_api.cobranca.application.port.out.dto.ResultadoNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Adapter SMTP via {@link JavaMailSender} (Sprint 13 - ADR 0014).
 *
 * <p>Ativado quando {@code app.notificacoes.provider=smtp-zenvia}. Renderiza o template HTML com
 * {@link TemplateNotificacaoEngine} e envia via {@link JavaMailSender}.
 *
 * <p>Falha tecnica controlada (destinatario invalido, validacao pre-envio) retorna
 * {@link ResultadoNotificacao#falha} pra que o use case grave evento de FALHA sem propagar excecao.
 * Falhas de infraestrutura ({@link MailException}) sao logadas e retornadas como falha — o use
 * case decide se faz retry no proximo dia.
 */
@Component
@ConditionalOnProperty(name = "app.notificacoes.provider", havingValue = "smtp-zenvia")
public class SmtpNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(SmtpNotificationProvider.class);
    private static final String NOME = "smtp";
    // Regex pragmatica — Spring/Jakarta validation tem outras mais completas; aqui basta
    // bloquear endereco vazio ou obviamente quebrado antes de bater no SMTP.
    private static final Pattern EMAIL_BASICO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final JavaMailSender mailSender;
    private final TemplateNotificacaoEngine templateEngine;
    private final NotificacaoProperties properties;

    public SmtpNotificationProvider(
            JavaMailSender mailSender, TemplateNotificacaoEngine templateEngine, NotificacaoProperties properties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    @Override
    public ResultadoNotificacao enviar(Notificacao notificacao) {
        if (!suporta(notificacao.canal())) {
            return ResultadoNotificacao.falha(NOME, "canal nao suportado: " + notificacao.canal());
        }
        if (!EMAIL_BASICO.matcher(notificacao.destinatario()).matches()) {
            return ResultadoNotificacao.falha(NOME, "destinatario invalido");
        }
        try {
            String html =
                    templateEngine.renderizar(CanalNotificacao.EMAIL, notificacao.template(), notificacao.variaveis());
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.remetenteEmail());
            helper.setTo(notificacao.destinatario());
            helper.setSubject(assuntoPorTemplate(notificacao.template()));
            helper.setText(html, true);
            mailSender.send(mime);
            return ResultadoNotificacao.sucesso(NOME, null);
        } catch (TemplateNotificacaoException e) {
            log.warn("Falha renderizando template {} para {}", notificacao.template(), NOME, e);
            return ResultadoNotificacao.falha(NOME, "render: " + e.getMessage());
        } catch (MessagingException | MailException e) {
            log.warn("Falha enviando email via SMTP (template={})", notificacao.template(), e);
            return ResultadoNotificacao.falha(NOME, "smtp: " + e.getMessage());
        }
    }

    @Override
    public boolean suporta(CanalNotificacao canal) {
        return canal == CanalNotificacao.EMAIL;
    }

    @Override
    public String nome() {
        return NOME;
    }

    /**
     * Assunto curto derivado do template — evita carregar o subject no DTO e mantem coerencia
     * editorial entre dev/test/prod.
     */
    private static String assuntoPorTemplate(String template) {
        return switch (template) {
            case "cobranca-amigavel" -> "Lembrete de pagamento — SEP";
            case "cobranca-firme" -> "Parcela em atraso — SEP";
            case "cobranca-final" -> "Atraso prolongado — acao necessaria";
            default -> "Comunicacao SEP";
        };
    }
}
