package com.dynamis.sep_api.backoffice.infrastructure.adapter.reprocesso.strategy;

import com.dynamis.sep_api.backoffice.application.port.out.ProviderRetentativaStrategy;
import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Strategy de retentativa de assinatura digital (Sprint 14 Task 14.4). */
@Component
public class AssinaturaDigitalRetentativaStrategy implements ProviderRetentativaStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(AssinaturaDigitalRetentativaStrategy.class);

    @Override
    public TipoChamadaProvider tipoSuportado() {
        return TipoChamadaProvider.ASSINATURA_DIGITAL;
    }

    @Override
    public ResultadoReprocesso retentar(UUID entidadeId) {
        LOG.info("retentativa ASSINATURA_DIGITAL registrada para contrato {}", entidadeId);
        return ResultadoReprocesso.sucesso("Retentativa de assinatura digital registrada para " + entidadeId);
    }
}
