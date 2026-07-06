package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.PixPagamentoParcelaResult;
import com.dynamis.sep_api.pix.application.mapper.StatusPixParcelaPublicoMapper;
import com.dynamis.sep_api.pix.application.port.out.ParcelaTomadorQueryPort;
import com.dynamis.sep_api.pix.domain.exception.PixLeituraNaoEncontradaException;
import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import com.dynamis.sep_api.pix.domain.model.PixReferenciaRecebimento;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixRecebimentoRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixReferenciaRecebimentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consulta owner-scoped do estado Pix de uma parcela do tomador (Sprint 26 — Gate P2). Valida
 * ownership da parcela ANTES de revelar qualquer estado Pix: parcela inexistente, parcela de outro
 * tomador e parcela sem estado Pix lancam a mesma {@link PixLeituraNaoEncontradaException} (404
 * neutro). O recebimento e buscado pela {@code referenciaId} da referencia atual — nunca o mais
 * recente da parcela — evitando casar uma referencia nova com um recebimento de referencia antiga.
 * Leitura local, sem gerar referencia, conciliar, reprocessar ou usar step-up.
 */
@Service
@Transactional(readOnly = true)
public class ConsultarStatusPixParcelaUseCase {

    private final ParcelaTomadorQueryPort parcelaTomadorPort;
    private final PixReferenciaRecebimentoRepository referenciaRepository;
    private final PixRecebimentoRepository recebimentoRepository;

    public ConsultarStatusPixParcelaUseCase(
            ParcelaTomadorQueryPort parcelaTomadorPort,
            PixReferenciaRecebimentoRepository referenciaRepository,
            PixRecebimentoRepository recebimentoRepository) {
        this.parcelaTomadorPort = parcelaTomadorPort;
        this.referenciaRepository = referenciaRepository;
        this.recebimentoRepository = recebimentoRepository;
    }

    public PixPagamentoParcelaResult executar(UUID parcelaId, UUID clienteAutenticadoId) {
        UUID owner =
                parcelaTomadorPort.tomadorIdDaParcela(parcelaId).orElseThrow(PixLeituraNaoEncontradaException::new);
        if (!owner.equals(clienteAutenticadoId)) {
            throw new PixLeituraNaoEncontradaException();
        }
        PixReferenciaRecebimento referencia = referenciaRepository
                .findFirstByParcelaIdOrderByDataCriacaoDesc(parcelaId)
                .orElseThrow(PixLeituraNaoEncontradaException::new);
        PixRecebimento recebimento = recebimentoRepository
                .findFirstByReferenciaIdOrderByDataCriacaoDesc(referencia.getId())
                .orElse(null);
        return StatusPixParcelaPublicoMapper.mapear(referencia, recebimento);
    }
}
