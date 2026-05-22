package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.RegistrarRecebimentoCommand;
import com.dynamis.sep_api.cobranca.application.dto.RegistrarRecebimentoResult;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.port.out.RegistrarMovimentacaoEscrowPort;
import com.dynamis.sep_api.cobranca.application.port.out.RegistrarMovimentacaoEscrowPort.MovimentacaoEscrowResult;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaPagaEvent;
import com.dynamis.sep_api.cobranca.domain.event.RecebimentoRegistradoEvent;
import com.dynamis.sep_api.cobranca.domain.exception.ChaveIdempotenciaConflitanteException;
import com.dynamis.sep_api.cobranca.domain.exception.ParcelaCobrancaNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.exception.ParcelaEstadoInvalidoException;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Recebimento;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RecebimentoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Registra recebimento manual de parcela (Sprint 12 Task 12.4) com:
 *
 * <ul>
 *   <li>Idempotencia por {@code Idempotency-Key} verificada em duas camadas: pre-lock (atalho
 *       quando ja existe) e pos-lock (cobre corrida entre threads concorrentes com a mesma key).
 *   <li>Validacao de consistencia da chave: reapresentacao com {@code parcelaId} ou
 *       {@code valorRecebido} divergente do recebimento existente lanca
 *       {@link ChaveIdempotenciaConflitanteException} (HTTP 409) — evita confundir abuso ou erro
 *       de cliente com retry legitimo.
 *   <li>Lock pessimista na parcela ({@link ParcelaCobrancaRepository#findByIdForUpdate(UUID)})
 *       serializando dois recebimentos distintos sobre a mesma parcela.
 *   <li>Estado da parcela calculado contra {@code valorTotal()} da composicao original. Task 12.5
 *       substituira por {@code CalcularValorAtualizadoParcelaUseCase} pra incluir juros/multa.
 *   <li>Movimentacao no escrow segregada via {@link RegistrarMovimentacaoEscrowPort} com mesma
 *       {@code idempotencyKey} — fluxo escrow herda a defesa de duplicacao.
 * </ul>
 *
 * <p>Eventos publicados:
 *
 * <ul>
 *   <li>{@link RecebimentoRegistradoEvent} sempre que novo recebimento for criado.
 *   <li>{@link ParcelaPagaEvent} quando a parcela transiciona pra {@link StatusParcela#PAGA}.
 * </ul>
 */
@Service
public class RegistrarRecebimentoUseCase {

    private final ParcelaCobrancaRepository parcelaRepository;
    private final RecebimentoRepository recebimentoRepository;
    private final RegistrarMovimentacaoEscrowPort escrowPort;
    private final ContratoCobrancaQueryPort contratoQueryPort;
    private final CalcularValorAtualizadoParcelaUseCase calcularValorAtualizado;
    private final ApplicationEventPublisher eventPublisher;

    public RegistrarRecebimentoUseCase(
            ParcelaCobrancaRepository parcelaRepository,
            RecebimentoRepository recebimentoRepository,
            RegistrarMovimentacaoEscrowPort escrowPort,
            ContratoCobrancaQueryPort contratoQueryPort,
            CalcularValorAtualizadoParcelaUseCase calcularValorAtualizado,
            ApplicationEventPublisher eventPublisher) {
        this.parcelaRepository = parcelaRepository;
        this.recebimentoRepository = recebimentoRepository;
        this.escrowPort = escrowPort;
        this.contratoQueryPort = contratoQueryPort;
        this.calcularValorAtualizado = calcularValorAtualizado;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public RegistrarRecebimentoResult executar(RegistrarRecebimentoCommand cmd) {
        Optional<Recebimento> existentePreLock = recebimentoRepository.findByIdempotencyKey(cmd.idempotencyKey());
        if (existentePreLock.isPresent()) {
            return resultadoExistente(cmd, existentePreLock.get());
        }
        return criarNovoRecebimento(cmd);
    }

    private RegistrarRecebimentoResult resultadoExistente(RegistrarRecebimentoCommand cmd, Recebimento existente) {
        validarConsistenciaIdempotencia(cmd, existente);
        ParcelaCobranca parcela = existente.getParcela();
        UUID propostaId = resolverPropostaId(parcela.getAgenda().getContratoId());
        MovimentacaoEscrowResult mov = escrowPort.registrarRecebimento(
                propostaId,
                existente.getValorRecebido(),
                existente.getIdempotencyKey(),
                existente.getDataRecebimento(),
                existente.getId());
        return new RegistrarRecebimentoResult(
                existente.getId(),
                parcela.getId(),
                parcela.getStatus(),
                existente.getValorRecebido(),
                mov.movimentacaoId(),
                false);
    }

    private RegistrarRecebimentoResult criarNovoRecebimento(RegistrarRecebimentoCommand cmd) {
        ParcelaCobranca parcela = parcelaRepository
                .findByIdForUpdate(cmd.parcelaId())
                .orElseThrow(() -> ParcelaCobrancaNaoEncontradaException.porId(cmd.parcelaId()));

        // Re-check pos-lock: outra thread pode ter persistido entre o pre-check e o lock.
        Optional<Recebimento> existentePosLock = recebimentoRepository.findByIdempotencyKey(cmd.idempotencyKey());
        if (existentePosLock.isPresent()) {
            return resultadoExistente(cmd, existentePosLock.get());
        }

        // Fail-fast: parcela em estado nao-recebivel evita calculo desnecessario de mora/multa.
        if (!parcela.getStatus().permiteRecebimento()) {
            throw new ParcelaEstadoInvalidoException("registrarRecebimento", parcela.getStatus());
        }

        // Sprint 12 Task 12.5: valor devido atualizado inclui juros mora + multa quando ha
        // atraso. Calcula sobre a parcela ja lockada — evita re-query.
        BigDecimal valorDevidoAtualizado =
                calcularValorAtualizado.calcular(parcela).valorDevidoAtualizado();

        Recebimento recebimento = parcela.registrarRecebimento(
                cmd.valorRecebido(),
                valorDevidoAtualizado,
                cmd.dataRecebimento(),
                cmd.meioPagamento(),
                cmd.identificadorExterno(),
                cmd.idempotencyKey(),
                cmd.observacao(),
                cmd.registradoPor());
        parcelaRepository.saveAndFlush(parcela);

        UUID contratoId = parcela.getAgenda().getContratoId();
        UUID propostaId = resolverPropostaId(contratoId);

        MovimentacaoEscrowResult mov = escrowPort.registrarRecebimento(
                propostaId, cmd.valorRecebido(), cmd.idempotencyKey(), cmd.dataRecebimento(), recebimento.getId());

        eventPublisher.publishEvent(new RecebimentoRegistradoEvent(
                recebimento.getId(),
                parcela.getId(),
                parcela.getAgenda().getId(),
                contratoId,
                recebimento.getValorRecebido(),
                recebimento.getDataRecebimento(),
                recebimento.getMeioPagamento(),
                recebimento.getRegistradoPor()));

        if (parcela.getStatus() == StatusParcela.PAGA) {
            eventPublisher.publishEvent(new ParcelaPagaEvent(
                    parcela.getId(),
                    parcela.getAgenda().getId(),
                    contratoId,
                    parcela.getNumero(),
                    parcela.totalRecebido()));
        }

        return new RegistrarRecebimentoResult(
                recebimento.getId(),
                parcela.getId(),
                parcela.getStatus(),
                cmd.valorRecebido(),
                mov.movimentacaoId(),
                true);
    }

    private void validarConsistenciaIdempotencia(RegistrarRecebimentoCommand cmd, Recebimento existente) {
        if (!existente.getParcela().getId().equals(cmd.parcelaId())) {
            throw new ChaveIdempotenciaConflitanteException(String.format(
                    "Idempotency-Key '%s' ja foi usada com parcela diferente (recebimento=%s, parcela esperada=%s, parcela informada=%s)",
                    cmd.idempotencyKey(),
                    existente.getId(),
                    existente.getParcela().getId(),
                    cmd.parcelaId()));
        }
        if (existente.getValorRecebido().compareTo(cmd.valorRecebido()) != 0) {
            throw new ChaveIdempotenciaConflitanteException(String.format(
                    "Idempotency-Key '%s' ja foi usada com valor diferente (esperado=%s, informado=%s)",
                    cmd.idempotencyKey(), existente.getValorRecebido(), cmd.valorRecebido()));
        }
    }

    private UUID resolverPropostaId(UUID contratoId) {
        return contratoQueryPort
                .propostaIdDoContrato(contratoId)
                .orElseThrow(() -> new IllegalStateException("Contrato sem propostaId associado: " + contratoId));
    }
}
