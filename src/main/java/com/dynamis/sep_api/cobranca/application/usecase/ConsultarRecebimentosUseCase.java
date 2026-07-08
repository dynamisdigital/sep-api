package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.port.out.RecebimentoCobrancaPort;
import com.dynamis.sep_api.cobranca.domain.model.Recebimento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lista recebimentos (Sprint 12 Task 12.6 — endpoint GET /cobranca/recebimentos). Acesso restrito
 * a ROLE_FINANCEIRO pelo controller. Ordenacao default: {@code dataRecebimento DESC}.
 *
 * <p>Usa fetch join em {@code parcela} (Task 12.6 fix code review manual) pra evitar N+1 + leitura
 * lazy fora da transacao no mapper.
 */
@Service
@Transactional(readOnly = true)
public class ConsultarRecebimentosUseCase {

    private final RecebimentoCobrancaPort recebimentoPort;

    public ConsultarRecebimentosUseCase(RecebimentoCobrancaPort recebimentoPort) {
        this.recebimentoPort = recebimentoPort;
    }

    public List<Recebimento> listar() {
        return recebimentoPort.listarTodosComParcelaOrdenadoPorDataDesc();
    }
}
