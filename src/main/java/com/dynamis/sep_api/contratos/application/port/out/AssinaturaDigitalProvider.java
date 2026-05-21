package com.dynamis.sep_api.contratos.application.port.out;

import com.dynamis.sep_api.contratos.application.port.out.dto.RequisicaoEnvioAssinatura;
import com.dynamis.sep_api.contratos.application.port.out.dto.RespostaEnvioAssinatura;
import com.dynamis.sep_api.contratos.application.port.out.dto.StatusEnvelopeProvider;

/**
 * Port de saida para integracao com provider de assinatura digital (Sprint 11 Task 11.4).
 *
 * <p>Provider Pattern (ADR 0004): port em linguagem de dominio + adapter em
 * {@code contratos.infrastructure.adapter.assinatura}. Provedor escolhido pela ADR 0013
 * (Clicksign), selecionado em runtime via {@code app.assinatura.provider=clicksign|fake}.
 *
 * <p>Contrato:
 * <ul>
 *   <li>{@link #enviarParaAssinatura} aceita PDF + metadados do signatario; retorna
 *       {@code idEnvelopeExterno} apos provider confirmar recebimento.
 *   <li>{@link #baixarDocumentoAssinado} recupera bytes do PDF assinado quando envelope
 *       finaliza em {@code ASSINADO}.
 *   <li>{@link #consultarStatus} retorna snapshot do status remoto (fonte canonica e o webhook;
 *       este metodo serve pra reconciliacao operacional).
 * </ul>
 *
 * <p>Headers obrigatorios (responsabilidade do adapter): {@code Authorization},
 * {@code Idempotency-Key} (carregado em {@code req.idempotencyKey()}), correlation id no MDC.
 */
public interface AssinaturaDigitalProvider {

    RespostaEnvioAssinatura enviarParaAssinatura(byte[] pdf, RequisicaoEnvioAssinatura req, String correlationId);

    byte[] baixarDocumentoAssinado(String idEnvelopeExterno);

    StatusEnvelopeProvider consultarStatus(String idEnvelopeExterno);
}
