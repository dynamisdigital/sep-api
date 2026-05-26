package com.dynamis.sep_api.backoffice.infrastructure.adapter.reprocesso.strategy;

import com.dynamis.sep_api.backoffice.application.port.out.ProviderRetentativaStrategy;
import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Strategy de retentativa de chamada KYC (Sprint 14 Task 14.4). Implementacao atual registra
 * intent — sprint futura conectara a {@code IniciarVerificacaoKycUseCase} com lookup completo de
 * contexto (usuario, payload do KYC, correlationId). O backoffice nao tem contexto suficiente
 * hoje pra reconstruir input do use case original; estrategia exige extensao explicita do modulo
 * {@code onboarding}.
 */
@Component
public class KycRetentativaStrategy implements ProviderRetentativaStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(KycRetentativaStrategy.class);

    @Override
    public TipoChamadaProvider tipoSuportado() {
        return TipoChamadaProvider.KYC;
    }

    @Override
    public ResultadoReprocesso retentar(UUID entidadeId) {
        LOG.info("retentativa KYC registrada para solicitacao {}", entidadeId);
        return ResultadoReprocesso.sucesso("Retentativa de KYC registrada para " + entidadeId);
    }
}
