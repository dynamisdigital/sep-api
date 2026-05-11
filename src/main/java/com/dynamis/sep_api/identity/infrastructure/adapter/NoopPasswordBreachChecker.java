package com.dynamis.sep_api.identity.infrastructure.adapter;

import com.dynamis.sep_api.identity.application.port.out.PasswordBreachChecker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Adapter default em dev-local: nao consulta HIBP, nunca marca senha como vazada (Sprint 5 Task
 * 5.5). Substituir por {@link HaveIBeenPwnedClient} em ambientes remotos via {@code
 * app.security.hibp.enabled=true}.
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.security.hibp.enabled", havingValue = "false", matchIfMissing = true)
public class NoopPasswordBreachChecker implements PasswordBreachChecker {

    @Override
    public boolean foiVazada(String senhaClara) {
        return false;
    }
}
