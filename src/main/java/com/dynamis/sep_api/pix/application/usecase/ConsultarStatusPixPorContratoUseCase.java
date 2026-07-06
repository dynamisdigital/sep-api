package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.StatusPixPublicoView;
import com.dynamis.sep_api.pix.application.mapper.StatusPixPublicoMapper;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Leitura publica do status de desembolso Pix mais recente de um contrato (Sprint 26), sem validar
 * ownership — o consumidor ({@code credores}, Gate P3) valida a posse antes de chamar. Reutiliza o
 * finder sem filtro de status e o {@link StatusPixPublicoMapper}, mantendo o mapa de status em fonte
 * unica. Retorna vazio quando o contrato nao tem desembolso Pix.
 */
@Service
@Transactional(readOnly = true)
public class ConsultarStatusPixPorContratoUseCase {

    private final PixTransferenciaRepository transferenciaRepository;

    public ConsultarStatusPixPorContratoUseCase(PixTransferenciaRepository transferenciaRepository) {
        this.transferenciaRepository = transferenciaRepository;
    }

    public Optional<StatusPixPublicoView> executar(UUID contratoId) {
        return transferenciaRepository
                .findFirstByContratoIdOrderByDataCriacaoDesc(contratoId)
                .map(transferencia -> new StatusPixPublicoView(
                        StatusPixPublicoMapper.mapear(transferencia.getStatus()),
                        transferencia.getValor(),
                        transferencia.getDataModificacao()));
    }
}
