package com.dynamis.sep_api.contratos.application.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Calcula hash SHA-256 (hex lowercase) do conteudo de uma versao de contrato. O hash e gravado em
 * {@code versao_contrato.hash_sha256} e funciona como evidencia de integridade da versao aceita —
 * qualquer alteracao posterior no texto rompe o hash gravado e a adulteracao fica detectavel.
 */
@Component
public class HashContratoService {

    public String calcular(String conteudoTexto) {
        if (conteudoTexto == null) {
            throw new IllegalArgumentException("conteudoTexto obrigatorio");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(conteudoTexto.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nao disponivel na JVM", e);
        }
    }
}
