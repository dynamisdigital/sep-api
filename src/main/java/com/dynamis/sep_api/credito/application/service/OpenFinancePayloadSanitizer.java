package com.dynamis.sep_api.credito.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Remove campos potencialmente sensiveis do payload Open Finance antes de persistir em
 * {@code movimentacao_open_finance.payload_consolidado} (Sprint 9 Task 9.3 — defesa LGPD em
 * profundidade).
 *
 * <p>Provider Celcoin/Finansystech deve responder com snapshot agregado, mas se contrato real
 * mudar e expor extrato bruto, este sanitizer filtra campos de risco. Lista de campos removidos
 * baseada em Open Finance Brasil API spec (Resolucao BCB 32/2020).
 *
 * <p>Estrategia conservadora: top-level objects e arrays sao mantidos; campos por nome em
 * {@link #CAMPOS_SENSIVEIS_REMOVIDOS} sao apagados recursivamente.
 *
 * <p><strong>Fail-closed</strong> (Sprint 9 fix code review Task 9.3): se o payload nao for JSON
 * valido, devolve placeholder {@code {"_sanitizer_error":"non-json","_size":N}} em vez do payload
 * bruto. Trade-off: perdemos detalhe pra forensics, mas garantimos que dados sensiveis nao vazam
 * na tabela {@code movimentacao_open_finance.payload_consolidado} caso provider externo mude
 * contrato e retorne texto plano com PII.
 */
@Component
public class OpenFinancePayloadSanitizer {

    private static final Logger log = LoggerFactory.getLogger(OpenFinancePayloadSanitizer.class);

    /** Campos removidos recursivamente do payload — dados identificaveis ou extrato bruto. */
    static final Set<String> CAMPOS_SENSIVEIS_REMOVIDOS = Set.of(
            "account_id",
            "account_number",
            "agency_number",
            "branch_code",
            "transactions",
            "raw_transactions",
            "transaction_list",
            "extrato",
            "cpf",
            "cnpj",
            "document_number",
            "holder_name",
            "holder_document");

    private final ObjectMapper objectMapper;

    public OpenFinancePayloadSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Devolve payload com campos sensiveis removidos. Fail-closed: se o payload nao for JSON
     * valido, devolve placeholder {@code {"_sanitizer_error":...,"_size":N}} pra nao persistir
     * potencial PII.
     */
    public String sanitize(String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            removerRecursivo(node);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            log.warn(
                    "Payload Open Finance nao e JSON valido (size={} chars): {}. Persistindo placeholder pra evitar vazamento LGPD.",
                    payload.length(),
                    ex.getMessage());
            return "{\"_sanitizer_error\":\"non-json\",\"_size\":" + payload.length() + "}";
        }
    }

    private void removerRecursivo(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            for (String campo : CAMPOS_SENSIVEIS_REMOVIDOS) {
                obj.remove(campo);
            }
            obj.fields().forEachRemaining(entry -> removerRecursivo(entry.getValue()));
        } else if (node.isArray()) {
            node.forEach(this::removerRecursivo);
        }
    }
}
