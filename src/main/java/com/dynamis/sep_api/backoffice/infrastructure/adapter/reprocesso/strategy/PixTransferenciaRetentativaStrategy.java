package com.dynamis.sep_api.backoffice.infrastructure.adapter.reprocesso.strategy;

import com.dynamis.sep_api.backoffice.application.port.out.ProviderRetentativaStrategy;
import com.dynamis.sep_api.backoffice.application.port.out.dto.ResultadoReprocesso;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import com.dynamis.sep_api.pix.application.dto.ConsultarStatusDesembolsoPixCommand;
import com.dynamis.sep_api.pix.application.dto.StatusDesembolsoPixResult;
import com.dynamis.sep_api.pix.application.usecase.ConsultarStatusDesembolsoPixUseCase;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Strategy de reprocesso de desembolso Pix (Sprint 20 Task 20.4). <strong>Operacao segura</strong>:
 * apenas reconsulta o status no provider e sincroniza a transferencia — <strong>nunca reenvia</strong>
 * a transferencia, porque a chave Pix destino nao eh persistida (minimizacao de dados). Para um novo
 * desembolso, o operador deve abrir uma nova solicitacao assistida com nova chave/Idempotency-Key.
 */
@Component
public class PixTransferenciaRetentativaStrategy implements ProviderRetentativaStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(PixTransferenciaRetentativaStrategy.class);

    private final ConsultarStatusDesembolsoPixUseCase consultarStatus;

    public PixTransferenciaRetentativaStrategy(ConsultarStatusDesembolsoPixUseCase consultarStatus) {
        this.consultarStatus = consultarStatus;
    }

    @Override
    public TipoChamadaProvider tipoSuportado() {
        return TipoChamadaProvider.PIX_TRANSFERENCIA;
    }

    @Override
    public ResultadoReprocesso retentar(UUID entidadeId) {
        try {
            StatusDesembolsoPixResult resultado =
                    consultarStatus.executar(new ConsultarStatusDesembolsoPixCommand(entidadeId, null));
            LOG.info("reprocesso PIX_TRANSFERENCIA reconsultou status={} para {}", resultado.status(), entidadeId);
            return ResultadoReprocesso.sucesso("Status do desembolso reconsultado: " + resultado.status()
                    + " (reenvio nao permitido — chave Pix nao persistida; abra nova solicitacao se necessario)");
        } catch (RecursoNaoEncontradoException ex) {
            return ResultadoReprocesso.falha("Transferencia Pix nao encontrada para reprocesso: " + entidadeId);
        }
    }
}
