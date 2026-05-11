package com.dynamis.sep_api.identity.domain.vo;

import com.dynamis.sep_api.identity.application.exception.SenhaComprometidaException;
import com.dynamis.sep_api.identity.application.exception.SenhaFracaException;
import com.dynamis.sep_api.identity.application.port.out.PasswordBreachChecker;
import org.springframework.stereotype.Component;

/**
 * Politica de senha NIST SP 800-63B (Sprint 5 Task 5.5).
 *
 * <ul>
 *   <li>Minimo 12 caracteres; <b>OU</b>
 *   <li>Passphrase com 4+ palavras separadas por espaco (cada palavra com >= 3 chars).
 * </ul>
 *
 * <p>Sem requisito de complexidade artificial (maiuscula/minuscula/digito/simbolo). Senhas vazadas
 * em bases publicas (HIBP) sao rejeitadas via {@link PasswordBreachChecker}.
 */
@Component
public class PasswordPolicy {

    static final int MIN_CHARS = 12;
    static final int MIN_PALAVRAS_PASSPHRASE = 4;
    static final int MIN_CHARS_POR_PALAVRA = 3;

    private final PasswordBreachChecker breachChecker;

    public PasswordPolicy(PasswordBreachChecker breachChecker) {
        this.breachChecker = breachChecker;
    }

    /** Valida a senha. Lanca {@link SenhaFracaException} ou {@link SenhaComprometidaException}. */
    public void validar(String senhaClara) {
        if (senhaClara == null || senhaClara.isBlank()) {
            throw new SenhaFracaException("Senha obrigatoria");
        }
        if (!atendeRegrasBasicas(senhaClara)) {
            throw new SenhaFracaException("minimo " + MIN_CHARS + " caracteres OU passphrase de "
                    + MIN_PALAVRAS_PASSPHRASE + "+ palavras de pelo menos " + MIN_CHARS_POR_PALAVRA + " caracteres");
        }
        if (breachChecker.foiVazada(senhaClara)) {
            throw new SenhaComprometidaException();
        }
    }

    boolean atendeRegrasBasicas(String senha) {
        if (senha.length() >= MIN_CHARS) {
            return true;
        }
        return ehPassphraseValida(senha);
    }

    private boolean ehPassphraseValida(String senha) {
        String[] palavras = senha.trim().split("\\s+");
        if (palavras.length < MIN_PALAVRAS_PASSPHRASE) {
            return false;
        }
        for (String palavra : palavras) {
            if (palavra.length() < MIN_CHARS_POR_PALAVRA) {
                return false;
            }
        }
        return true;
    }
}
