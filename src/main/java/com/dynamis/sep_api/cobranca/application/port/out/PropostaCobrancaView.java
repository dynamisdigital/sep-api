package com.dynamis.sep_api.cobranca.application.port.out;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Projecao read-only de {@code PropostaCredito} consumida por {@code cobranca} (Sprint 12 Task
 * 12.3). Exposicao minima — apenas o que cobranca precisa pra gerar agenda. Evita acoplamento
 * direto ao agregado {@code PropostaCredito}.
 */
public record PropostaCobrancaView(
        UUID id, BigDecimal valorSolicitado, int prazoMeses, Optional<BigDecimal> taxaJurosMensal) {}
