package com.dynamis.sep_api.contratos.infrastructure.adapter.assinatura;

import com.dynamis.sep_api.contratos.application.port.out.AssinaturaDigitalProvider;
import com.dynamis.sep_api.contratos.application.port.out.dto.RequisicaoEnvioAssinatura;
import com.dynamis.sep_api.contratos.application.port.out.dto.RespostaEnvioAssinatura;
import com.dynamis.sep_api.contratos.application.port.out.dto.StatusEnvelopeProvider;
import com.dynamis.sep_api.contratos.domain.vo.StatusEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Adapter fake do {@link AssinaturaDigitalProvider} (Sprint 11 Task 11.4). Sem rede, sem
 * credenciais. Ativado quando {@code app.assinatura.provider=fake} (default em dev/test).
 *
 * <p>Comportamento:
 * <ul>
 *   <li>{@code enviarParaAssinatura} gera {@code idEnvelopeExterno} deterministico a partir de
 *       {@code idempotencyKey} (reenvio com mesma chave devolve o mesmo id);
 *   <li>{@code baixarDocumentoAssinado} retorna PDF stub (magic {@code %PDF} + bytes) — IT da
 *       Task 11.9 sobrescreve quando precisa de PDF real;
 *   <li>{@code consultarStatus} retorna {@code ASSINADO} por padrao; testes podem mudar via
 *       {@link #setStatus(String, StatusEnvelope)}.
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.assinatura.provider", havingValue = "fake", matchIfMissing = true)
public class FakeAssinaturaDigitalProvider implements AssinaturaDigitalProvider {

    private static final Logger log = LoggerFactory.getLogger(FakeAssinaturaDigitalProvider.class);
    private static final byte[] PDF_STUB = "%PDF-1.4 fake-assinado".getBytes();

    private final ConcurrentMap<String, StatusEnvelope> statusPorEnvelope = new ConcurrentHashMap<>();

    @Override
    public RespostaEnvioAssinatura enviarParaAssinatura(
            byte[] pdf, RequisicaoEnvioAssinatura req, String correlationId) {
        String idEnvelopeExterno = "fake-env-" + req.idempotencyKey();
        statusPorEnvelope.putIfAbsent(idEnvelopeExterno, StatusEnvelope.ENVIADO);
        log.info(
                "FakeAssinaturaDigitalProvider.enviar contratoId={} idEnvelopeExterno={} correlationId={}",
                req.contratoId(),
                idEnvelopeExterno,
                correlationId);
        return new RespostaEnvioAssinatura(idEnvelopeExterno, OffsetDateTime.now());
    }

    @Override
    public byte[] baixarDocumentoAssinado(String idEnvelopeExterno) {
        return PDF_STUB.clone();
    }

    @Override
    public StatusEnvelopeProvider consultarStatus(String idEnvelopeExterno) {
        StatusEnvelope status = statusPorEnvelope.getOrDefault(idEnvelopeExterno, StatusEnvelope.ASSINADO);
        return new StatusEnvelopeProvider(status, OffsetDateTime.now());
    }

    /** Hook pra testes simularem transicoes (visualizado, recusado, expirado). */
    public void setStatus(String idEnvelopeExterno, StatusEnvelope status) {
        statusPorEnvelope.put(idEnvelopeExterno, status);
    }
}
