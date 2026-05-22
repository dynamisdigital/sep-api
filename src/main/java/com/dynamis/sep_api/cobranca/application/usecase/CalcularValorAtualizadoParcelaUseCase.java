package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.ParcelaAtualizadaResult;
import com.dynamis.sep_api.cobranca.application.service.calculo.CalculadoraJurosMora;
import com.dynamis.sep_api.cobranca.application.service.calculo.CalculadoraMulta;
import com.dynamis.sep_api.cobranca.application.service.calculo.ParametrosCobrancaProperties;
import com.dynamis.sep_api.cobranca.domain.exception.ParcelaCobrancaNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.ComposicaoValor;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Calcula a composicao atualizada de uma {@link ParcelaCobranca} contra {@code LocalDate.now(clock)}
 * (Sprint 12 Task 12.5). Fronteira de calculo de mora — o agregado nao recalcula sozinho pra manter
 * {@link Clock} testavel e isolar regras tarifarias do dominio.
 *
 * <p>Base da mora: saldo nominal em aberto ({@code composicaoOriginal.total() - totalRecebido},
 * clampado em zero). Quando a parcela esta {@link StatusParcela#PAGA}, retorna zeros sem chamar as
 * calculadoras.
 */
@Service
@Transactional(readOnly = true)
public class CalcularValorAtualizadoParcelaUseCase {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final ParcelaCobrancaRepository parcelaRepository;
    private final CalculadoraJurosMora calculadoraJurosMora;
    private final CalculadoraMulta calculadoraMulta;
    private final ParametrosCobrancaProperties properties;
    private final Clock clock;

    public CalcularValorAtualizadoParcelaUseCase(
            ParcelaCobrancaRepository parcelaRepository,
            CalculadoraJurosMora calculadoraJurosMora,
            CalculadoraMulta calculadoraMulta,
            ParametrosCobrancaProperties properties,
            Clock clock) {
        this.parcelaRepository = parcelaRepository;
        this.calculadoraJurosMora = calculadoraJurosMora;
        this.calculadoraMulta = calculadoraMulta;
        this.properties = properties;
        this.clock = clock;
    }

    public ParcelaAtualizadaResult executar(UUID parcelaId) {
        ParcelaCobranca parcela = parcelaRepository
                .findById(parcelaId)
                .orElseThrow(() -> ParcelaCobrancaNaoEncontradaException.porId(parcelaId));
        return calcular(parcela);
    }

    /**
     * Resolve apenas o {@code contratoId} da parcela sem retornar dados financeiros (Sprint 12
     * Task 12.6 fix code review manual). Usado pelo controller para validar ownership ANTES de
     * carregar/calcular — evita enumeracao 404 vs 403.
     */
    public java.util.Optional<UUID> resolverContratoId(UUID parcelaId) {
        return parcelaRepository.findById(parcelaId).map(p -> p.getAgenda().getContratoId());
    }

    /**
     * Variante para callers que ja tem a parcela em mao (Sprint 12 Task 12.4 — chamado pelo
     * {@code RegistrarRecebimentoUseCase} apos {@code findByIdForUpdate}). Evita re-query e
     * preserva o estado lockado.
     */
    public ParcelaAtualizadaResult calcular(ParcelaCobranca parcela) {
        LocalDate hoje = LocalDate.now(clock);
        ComposicaoValor composicaoOriginal = parcela.composicaoOriginal();
        BigDecimal totalRecebido = parcela.totalRecebido();

        if (parcela.getStatus() == StatusParcela.PAGA) {
            return new ParcelaAtualizadaResult(
                    parcela.getId(),
                    parcela.getAgenda().getContratoId(),
                    parcela.getNumero(),
                    parcela.getStatus(),
                    parcela.getDataVencimento(),
                    composicaoOriginal,
                    ZERO,
                    ZERO,
                    composicaoOriginal.total(),
                    totalRecebido,
                    ZERO);
        }

        BigDecimal saldoNominal = composicaoOriginal.total().subtract(totalRecebido);
        BigDecimal base = saldoNominal.signum() < 0 ? ZERO : saldoNominal;

        BigDecimal jurosMora =
                calculadoraJurosMora.calcular(base, parcela.getDataVencimento(), hoje, properties.getJurosMoraMensal());
        BigDecimal multa =
                calculadoraMulta.calcular(base, parcela.getDataVencimento(), hoje, properties.getMultaAtraso());

        BigDecimal valorDevidoBruto = composicaoOriginal.total().add(jurosMora).add(multa);
        BigDecimal emAberto = valorDevidoBruto.subtract(totalRecebido);
        BigDecimal valorEmAberto = emAberto.signum() < 0 ? ZERO : emAberto;

        return new ParcelaAtualizadaResult(
                parcela.getId(),
                parcela.getAgenda().getContratoId(),
                parcela.getNumero(),
                parcela.getStatus(),
                parcela.getDataVencimento(),
                composicaoOriginal,
                jurosMora,
                multa,
                valorDevidoBruto,
                totalRecebido,
                valorEmAberto);
    }
}
