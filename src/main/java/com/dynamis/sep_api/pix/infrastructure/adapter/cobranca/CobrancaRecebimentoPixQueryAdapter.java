package com.dynamis.sep_api.pix.infrastructure.adapter.cobranca;

import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.usecase.CalcularValorAtualizadoParcelaUseCase;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.pix.application.port.out.CobrancaRecebimentoPixQueryPort;
import com.dynamis.sep_api.pix.application.port.out.dto.ParcelaRecebimentoPixView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter que traduz {@link CobrancaRecebimentoPixQueryPort} para {@code cobranca} (Sprint 21 Task
 * 21.2). Le a parcela, delega o calculo do valor em aberto ao
 * {@link CalcularValorAtualizadoParcelaUseCase} (o {@code pix} nao recalcula mora/multa) e resolve
 * o {@code tomadorId} pela porta de {@code cobranca}. O dominio {@code pix} so recebe a
 * {@link ParcelaRecebimentoPixView} — nunca a entidade de cobranca.
 */
@Component
public class CobrancaRecebimentoPixQueryAdapter implements CobrancaRecebimentoPixQueryPort {

    private final ParcelaCobrancaRepository parcelaRepository;
    private final CalcularValorAtualizadoParcelaUseCase calcularValorAtualizado;
    private final ContratoCobrancaQueryPort contratoQueryPort;

    public CobrancaRecebimentoPixQueryAdapter(
            ParcelaCobrancaRepository parcelaRepository,
            CalcularValorAtualizadoParcelaUseCase calcularValorAtualizado,
            ContratoCobrancaQueryPort contratoQueryPort) {
        this.parcelaRepository = parcelaRepository;
        this.calcularValorAtualizado = calcularValorAtualizado;
        this.contratoQueryPort = contratoQueryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ParcelaRecebimentoPixView> buscarParcelaParaReferenciaPix(UUID parcelaId) {
        // readOnly=true torna explicito o contrato: a montagem da view traversa associacoes LAZY
        // (agenda; recebimentos via CalcularValorAtualizadoParcelaUseCase) e exige persistence
        // context aberto. O caller (GerarReferenciaRecebimentoPixUseCase) ja eh @Transactional, mas
        // a anotacao garante robustez tambem fora desse fluxo.
        return parcelaRepository.findById(parcelaId).map(this::montarView);
    }

    private ParcelaRecebimentoPixView montarView(ParcelaCobranca parcela) {
        UUID contratoId = parcela.getAgenda().getContratoId();
        UUID tomadorId = contratoQueryPort
                .tomadorIdDoContrato(contratoId)
                .orElseThrow(() -> new IllegalStateException("Contrato sem tomadorId associado: " + contratoId));
        BigDecimal valorEmAberto = calcularValorAtualizado.calcular(parcela).valorEmAberto();
        return new ParcelaRecebimentoPixView(
                parcela.getId(),
                contratoId,
                tomadorId,
                valorEmAberto,
                parcela.getStatus().permiteRecebimento());
    }
}
