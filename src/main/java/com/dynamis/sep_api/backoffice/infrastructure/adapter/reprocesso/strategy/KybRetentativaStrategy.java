package com.dynamis.sep_api.backoffice.infrastructure.adapter.reprocesso.strategy;

import com.dynamis.sep_api.backoffice.application.port.out.ProviderRetentativaStrategy;
import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Strategy de retentativa KYB (Sprint 14 Task 14.4). Mesma estrutura de {@code KycRetentativaStrategy}. */
@Component
public class KybRetentativaStrategy implements ProviderRetentativaStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(KybRetentativaStrategy.class);

    @Override
    public TipoChamadaProvider tipoSuportado() {
        return TipoChamadaProvider.KYB;
    }

    @Override
    public ResultadoReprocesso retentar(UUID entidadeId) {
        LOG.info("retentativa KYB registrada para solicitacao {}", entidadeId);
        return ResultadoReprocesso.sucesso("Retentativa de KYB registrada para " + entidadeId);
    }
}
