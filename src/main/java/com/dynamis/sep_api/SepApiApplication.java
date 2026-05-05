package com.dynamis.sep_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Bootstrap da aplicacao SEP API (Spring Boot 3.5).
 *
 * <p>Anotada com {@code @SpringBootApplication} a partir da Sprint 1 Task 1.1b. O autowiring de
 * componentes acontece a partir do package raiz {@code com.dynamis.sep_api}, cobrindo
 * automaticamente os 12 modulos de dominio (identity, usuarios, onboarding, credito, contratos,
 * cobranca, escrow, backoffice, financeiro, credores, pix, shared).
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} fica desligada porque a Sprint 3 plugara o filtro
 * JWT proprio; sem essa exclusao o Spring Security cria um usuario {@code user} com senha gerada
 * que aparece no log e nao tem uso real.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SepApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SepApiApplication.class, args);
    }
}
