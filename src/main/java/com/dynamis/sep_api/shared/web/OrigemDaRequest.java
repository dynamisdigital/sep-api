package com.dynamis.sep_api.shared.web;

/**
 * Origem de rede de uma request, normalizada para armazenamento (Sprint 35 Task 35.2).
 *
 * <p>Mora em {@code shared} porque tem <b>dois</b> consumidores em modulos diferentes — o rate limit
 * (identity) e a trilha de aceite de contrato (contratos) — e a alternativa seria um deles importar
 * a infraestrutura do outro, ou o numero 45 aparecer duas vezes.
 *
 * <p><b>Por que cortar.</b> O valor nem sempre e um endereco de socket: com
 * {@code server.forward-headers-strategy: native}, requests vindas de um proxy no allowlist chegam
 * com o {@code getRemoteAddr()} ja reescrito pelo {@code RemoteIpValve} a partir do
 * {@code X-Forwarded-For} — e o valve <b>nao valida</b> que o token e um IP. Sem corte, um token
 * longo (a) infla o mapa de limitadores, cuja chave e a origem, e (b) estoura as colunas
 * {@code VARCHAR(45)} de {@code login_attempt.ip} e {@code audit_log_seguranca.ip}, abortando o
 * insert do rastro — no caso do audit, dentro de um listener {@code AFTER_COMMIT}, ou seja perdendo
 * a evidencia sem desfazer a operacao.
 *
 * <p>45 e o comprimento de um IPv6 com mapeamento IPv4 e zona, e o mesmo das colunas. Acima disso
 * tudo cai no balde {@code unknown} — mais estrito, nunca mais permissivo.
 */
public final class OrigemDaRequest {

    /** Igual ao {@code length} das colunas {@code ip}. Mudar aqui exige migration. */
    public static final int MAX_TAMANHO = 45;

    private static final String DESCONHECIDA = "unknown";

    private OrigemDaRequest() {}

    public static String normalizar(String origem) {
        return origem == null || origem.isBlank() || origem.length() > MAX_TAMANHO ? DESCONHECIDA : origem;
    }
}
