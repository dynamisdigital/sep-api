package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.RecebimentoTomadorResult;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.domain.exception.CobrancaOwnershipException;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RecebimentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Consulta owner-scoped do historico de recebimentos de uma parcela (Sprint 23 — desbloqueio B1 da
 * M-Sprint 9). Valida ownership ANTES de consultar recebimentos: parcela inexistente e parcela de
 * outro tomador lancam a mesma {@link CobrancaOwnershipException} generica (sem UUID), impedindo
 * enumeracao. Nao reutiliza a listagem operacional de FINANCEIRO/ADMIN.
 */
@Service
@Transactional(readOnly = true)
public class ConsultarRecebimentosParcelaUseCase {

    private final ParcelaCobrancaRepository parcelaRepository;
    private final ContratoCobrancaQueryPort contratoQueryPort;
    private final RecebimentoRepository recebimentoRepository;

    public ConsultarRecebimentosParcelaUseCase(
            ParcelaCobrancaRepository parcelaRepository,
            ContratoCobrancaQueryPort contratoQueryPort,
            RecebimentoRepository recebimentoRepository) {
        this.parcelaRepository = parcelaRepository;
        this.contratoQueryPort = contratoQueryPort;
        this.recebimentoRepository = recebimentoRepository;
    }

    public List<RecebimentoTomadorResult> executar(UUID parcelaId, UUID tomadorAutenticadoId) {
        ParcelaCobranca parcela = parcelaRepository.findById(parcelaId).orElseThrow(CobrancaOwnershipException::new);
        UUID contratoId = parcela.getAgenda().getContratoId();
        UUID owner = contratoQueryPort.tomadorIdDoContrato(contratoId).orElseThrow(CobrancaOwnershipException::new);
        if (!owner.equals(tomadorAutenticadoId)) {
            throw new CobrancaOwnershipException();
        }
        return recebimentoRepository.findByParcela_IdOrderByDataRecebimentoDesc(parcelaId).stream()
                .map(r -> new RecebimentoTomadorResult(
                        r.getId(), r.getValorRecebido(), r.getDataRecebimento(), r.getMeioPagamento()))
                .toList();
    }
}
