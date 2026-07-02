package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.RenegociacaoTomadorResult;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.domain.exception.CobrancaOwnershipException;
import com.dynamis.sep_api.cobranca.domain.exception.RenegociacaoNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Renegociacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusRenegociacao;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RenegociacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consulta owner-scoped da renegociacao ativa de uma parcela (Sprint 24 — desbloqueio B2 da
 * M-Sprint 9). Valida ownership ANTES de revelar se existe proposta: parcela inexistente e parcela
 * de outro tomador lancam a mesma {@link CobrancaOwnershipException} generica (sem UUID), impedindo
 * enumeracao.
 *
 * <p>Read-only: nao dispara o job de expiracao nem persiste nada. So retorna {@link
 * StatusRenegociacao#PROPOSTA} ainda nao expirada pelo {@link Clock} — proposta vencida antes da
 * varredura do job e tratada como inexistente ({@code 404}).
 */
@Service
@Transactional(readOnly = true)
public class ConsultarRenegociacaoAtivaTomadorUseCase {

    private final ParcelaCobrancaRepository parcelaRepository;
    private final ContratoCobrancaQueryPort contratoQueryPort;
    private final RenegociacaoRepository renegociacaoRepository;
    private final Clock clock;

    public ConsultarRenegociacaoAtivaTomadorUseCase(
            ParcelaCobrancaRepository parcelaRepository,
            ContratoCobrancaQueryPort contratoQueryPort,
            RenegociacaoRepository renegociacaoRepository,
            Clock clock) {
        this.parcelaRepository = parcelaRepository;
        this.contratoQueryPort = contratoQueryPort;
        this.renegociacaoRepository = renegociacaoRepository;
        this.clock = clock;
    }

    public RenegociacaoTomadorResult executar(UUID parcelaId, UUID tomadorAutenticadoId) {
        exigirOwnership(parcelaId, tomadorAutenticadoId);
        Renegociacao ativa = renegociacaoRepository
                .findByParcelaOriginalIdAndStatus(parcelaId, StatusRenegociacao.PROPOSTA)
                .filter(r -> !r.expirouEm(OffsetDateTime.now(clock)))
                .orElseThrow(RenegociacaoNaoEncontradaException::semPropostaAtiva);
        return toResult(ativa);
    }

    private void exigirOwnership(UUID parcelaId, UUID tomadorAutenticadoId) {
        ParcelaCobranca parcela =
                parcelaRepository.findById(parcelaId).orElseThrow(CobrancaOwnershipException::new);
        UUID owner = contratoQueryPort
                .tomadorIdDoContrato(parcela.getAgenda().getContratoId())
                .orElseThrow(CobrancaOwnershipException::new);
        if (!owner.equals(tomadorAutenticadoId)) {
            throw new CobrancaOwnershipException();
        }
    }

    private static RenegociacaoTomadorResult toResult(Renegociacao r) {
        BigDecimal valorTotal = r.getNovoValorParcela().multiply(BigDecimal.valueOf(r.getNumeroParcelas()));
        return new RenegociacaoTomadorResult(
                r.getId(),
                r.getParcelaOriginalId(),
                r.getStatus(),
                r.getNovoValorParcela(),
                r.getNumeroParcelas(),
                valorTotal,
                r.getNovoVencimento(),
                r.getDesconto(),
                r.getDataProposta(),
                r.getDataExpiracao());
    }
}
