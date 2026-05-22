package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.domain.model.Recebimento;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RecebimentoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lista recebimentos (Sprint 12 Task 12.6 — endpoint GET /cobranca/recebimentos). Acesso restrito
 * a ROLE_FINANCEIRO pelo controller. Ordenacao default: {@code dataRecebimento DESC}.
 */
@Service
@Transactional(readOnly = true)
public class ConsultarRecebimentosUseCase {

    private final RecebimentoRepository recebimentoRepository;

    public ConsultarRecebimentosUseCase(RecebimentoRepository recebimentoRepository) {
        this.recebimentoRepository = recebimentoRepository;
    }

    public List<Recebimento> listar() {
        return recebimentoRepository.findAll(Sort.by(Sort.Direction.DESC, "dataRecebimento"));
    }
}
