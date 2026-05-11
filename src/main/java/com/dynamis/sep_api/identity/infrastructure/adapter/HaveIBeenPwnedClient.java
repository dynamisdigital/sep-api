package com.dynamis.sep_api.identity.infrastructure.adapter;

import com.dynamis.sep_api.identity.application.port.out.PasswordBreachChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Cliente HaveIBeenPwned via k-anonymity API (Sprint 5 Task 5.5). Envia somente os 5 primeiros
 * caracteres do SHA-1 hex da senha; recebe a lista de sufixos vazados e checa se o sufixo da senha
 * esta presente.
 *
 * <p>Ativado por {@code app.security.hibp.enabled=true}. Em falha (rede/timeout), retorna {@code
 * false} para nao bloquear o cadastro/alteracao de senha por indisponibilidade externa.
 */
@Component
@ConditionalOnProperty(name = "app.security.hibp.enabled", havingValue = "true")
public class HaveIBeenPwnedClient implements PasswordBreachChecker {

    private static final Logger log = LoggerFactory.getLogger(HaveIBeenPwnedClient.class);

    private final RestClient restClient;

    public HaveIBeenPwnedClient(@Value("${app.security.hibp.base-url:https://api.pwnedpasswords.com}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "SEP-API")
                .build();
    }

    @Override
    public boolean foiVazada(String senhaClara) {
        try {
            String hash = sha1Hex(senhaClara);
            String prefixo = hash.substring(0, 5);
            String sufixoEsperado = hash.substring(5).toUpperCase();
            String corpo =
                    restClient.get().uri("/range/{prefixo}", prefixo).retrieve().body(String.class);
            if (corpo == null) {
                return false;
            }
            for (String linha : corpo.split("\\r?\\n")) {
                int sep = linha.indexOf(':');
                if (sep > 0 && linha.substring(0, sep).equalsIgnoreCase(sufixoEsperado)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            log.warn("HIBP indisponivel: {}", ex.getMessage());
            return false;
        }
    }

    private static String sha1Hex(String texto) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            return HexFormat.of()
                    .formatHex(sha1.digest(texto.getBytes(StandardCharsets.UTF_8)))
                    .toUpperCase();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 nao disponivel", ex);
        }
    }
}
