package com.dynamis.sep_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap da aplicacao SEP API (Spring Boot 3.5).
 *
 * <p>Anotada com {@code @SpringBootApplication} a partir da Sprint 1 Task 1.1b. O autowiring de
 * componentes acontece a partir do package raiz {@code com.dynamis.sep_api}, cobrindo
 * automaticamente os 12 modulos de dominio (identity, usuarios, onboarding, credito, contratos,
 * cobranca, escrow, backoffice, financeiro, credores, pix, shared).
 */
@SpringBootApplication
public class SepApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SepApiApplication.class, args);
    }
}
