package com.dynamis.sep_api.backoffice.infrastructure.adapter.reprocesso.strategy;

import com.dynamis.sep_api.backoffice.application.port.out.ProviderRetentativaStrategy;
import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Strategy de retentativa PLD (Sprint 14 Task 14.4). */
@Component
public class PldRetentativaStrategy implements ProviderRetentativaStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(PldRetentativaStrategy.class);

    @Override
    public TipoChamadaProvider tipoSuportado() {
        return TipoChamadaProvider.PLD;
    }

    @Override
    public ResultadoReprocesso retentar(UUID entidadeId) {
        LOG.info("retentativa PLD registrada para solicitacao {}", entidadeId);
        return ResultadoReprocesso.sucesso("Retentativa de PLD registrada para " + entidadeId);
    }
}
