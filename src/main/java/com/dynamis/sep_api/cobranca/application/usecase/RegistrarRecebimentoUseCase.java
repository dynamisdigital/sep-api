package com.dynamis.sep_api.cobranca.application.usecase;

import com.dynamis.sep_api.cobranca.application.dto.RegistrarRecebimentoCommand;
import com.dynamis.sep_api.cobranca.application.dto.RegistrarRecebimentoResult;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.port.out.RegistrarMovimentacaoEscrowPort;
import com.dynamis.sep_api.cobranca.application.port.out.RegistrarMovimentacaoEscrowPort.MovimentacaoEscrowResult;
import com.dynamis.sep_api.cobranca.domain.event.ParcelaPagaEvent;
import com.dynamis.sep_api.cobranca.domain.event.RecebimentoRegistradoEvent;
import com.dynamis.sep_api.cobranca.domain.exception.ParcelaCobrancaNaoEncontradaException;
import com.dynamis.sep_api.cobranca.domain.model.ParcelaCobranca;
import com.dynamis.sep_api.cobranca.domain.model.Recebimento;
import com.dynamis.sep_api.cobranca.domain.vo.StatusParcela;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.ParcelaCobrancaRepository;
import com.dynamis.sep_api.cobranca.infrastructure.persistence.RecebimentoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Registra recebimento manual de parcela (Sprint 12 Task 12.4) com:
 *
 * <ul>
 *   <li>Idempotencia por {@code Idempotency-Key} — chamadas repetidas retornam o mesmo
 *       {@code Recebimento} sem criar nova movimentacao escrow nem duplicar credito.
 *   <li>Lock pessimista na parcela ({@link ParcelaCobrancaRepository#findByIdForUpdate(UUID)})
 *       serializando dois recebimentos concorrentes sobre a mesma parcela.
 *   <li>Estado da parcela calculado contra {@code valorTotal()} da composicao original. Task 12.5
 *       substituira por {@code CalcularValorAtualizadoParcelaUseCase} pra considerar mora.
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
    private final ApplicationEventPublisher eventPublisher;

    public RegistrarRecebimentoUseCase(
            ParcelaCobrancaRepository parcelaRepository,
            RecebimentoRepository recebimentoRepository,
            RegistrarMovimentacaoEscrowPort escrowPort,
            ContratoCobrancaQueryPort contratoQueryPort,
            ApplicationEventPublisher eventPublisher) {
        this.parcelaRepository = parcelaRepository;
        this.recebimentoRepository = recebimentoRepository;
        this.escrowPort = escrowPort;
        this.contratoQueryPort = contratoQueryPort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public RegistrarRecebimentoResult executar(RegistrarRecebimentoCommand cmd) {
        return recebimentoRepository
                .findByIdempotencyKey(cmd.idempotencyKey())
                .map(existente -> resultadoExistente(existente))
                .orElseGet(() -> criarNovoRecebimento(cmd));
    }

    private RegistrarRecebimentoResult resultadoExistente(Recebimento r) {
        ParcelaCobranca parcela = r.getParcela();
        // Movimentacao escrow ja existente — a port eh idempotente por idempotencyKey,
        // entao buscar via port garante o id mesmo nesta reapresentacao.
        UUID propostaId = contratoQueryPort
                .propostaIdDoContrato(parcela.getAgenda().getContratoId())
                .orElseThrow(() -> new IllegalStateException("Contrato sem propostaId associado: "
                        + parcela.getAgenda().getContratoId()));
        MovimentacaoEscrowResult mov = escrowPort.registrarRecebimento(
                propostaId, r.getValorRecebido(), r.getIdempotencyKey(), r.getDataRecebimento(), r.getId());
        return new RegistrarRecebimentoResult(
                r.getId(), parcela.getId(), parcela.getStatus(), r.getValorRecebido(), mov.movimentacaoId(), false);
    }

    private RegistrarRecebimentoResult criarNovoRecebimento(RegistrarRecebimentoCommand cmd) {
        ParcelaCobranca parcela = parcelaRepository
                .findByIdForUpdate(cmd.parcelaId())
                .orElseThrow(() -> ParcelaCobrancaNaoEncontradaException.porId(cmd.parcelaId()));

        // Sprint 12 Task 12.4: valor devido atualizado = valor total original (sem mora ainda).
        // Task 12.5 substituira por CalcularValorAtualizadoParcelaUseCase pra incluir juros/multa.
        BigDecimal valorDevidoAtualizado = parcela.valorTotal();

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
        UUID propostaId = contratoQueryPort
                .propostaIdDoContrato(contratoId)
                .orElseThrow(() -> new IllegalStateException("Contrato sem propostaId associado: " + contratoId));

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
}
