package com.dynamis.sep_api.backoffice.infrastructure.adapter.reprocesso.strategy;

import com.dynamis.sep_api.backoffice.application.port.out.ProviderRetentativaStrategy;
import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Strategy de retentativa Open Finance (Sprint 14 Task 14.4). */
@Component
public class OpenFinanceRetentativaStrategy implements ProviderRetentativaStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(OpenFinanceRetentativaStrategy.class);

    @Override
    public TipoChamadaProvider tipoSuportado() {
        return TipoChamadaProvider.OPEN_FINANCE;
    }

    @Override
    public ResultadoReprocesso retentar(UUID entidadeId) {
        LOG.info("retentativa OPEN_FINANCE registrada para consentimento {}", entidadeId);
        return ResultadoReprocesso.sucesso("Retentativa de Open Finance registrada para " + entidadeId);
    }
}
