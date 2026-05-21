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
        return calcular(conteudoTexto.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Calcula SHA-256 hex lowercase de bytes arbitrarios (Sprint 11). Usado pra hash do PDF da
     * CCB enviado (gravado em {@code envelope_assinatura.hash_pdf_enviado}) e do PDF assinado
     * baixado do provider (gravado em {@code documento_assinado.hash_sha256}).
     */
    public String calcular(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes obrigatorio");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nao disponivel na JVM", e);
        }
    }
}
