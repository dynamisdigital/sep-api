package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.port.out.CobrancaRecebimentoPixPort;
import com.dynamis.sep_api.pix.application.port.out.dto.RecebimentoPixCobrancaResult;
import com.dynamis.sep_api.pix.application.port.out.dto.RegistrarRecebimentoPixCobrancaCommand;
import com.dynamis.sep_api.pix.domain.model.PixRecebimento;
import com.dynamis.sep_api.pix.domain.model.PixReferenciaRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixRecebimentoRepository;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixReferenciaRecebimentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Concilia um recebimento Pix identificado com a parcela de cobranca (Sprint 21 Task 21.4). A baixa
 * passa pelo caminho oficial de {@code cobranca} via {@link CobrancaRecebimentoPixPort} — que detem
 * lock, calculo de valor devido, status da parcela, criacao do {@code Recebimento} e a movimentacao
 * escrow idempotente. O {@code pix} apenas correlaciona e registra o resultado.
 *
 * <p>Transacao: cada metodo abre a propria tx {@link Propagation#REQUIRES_NEW}. A
 * {@link #conciliar(UUID)} torna a baixa + movimentacao escrow + vinculo de conciliacao atomicos; se
 * a baixa falhar (parcela nao recebivel, conflito de chave, erro tecnico), a tx reverte limpa e o
 * caller marca o recebimento como {@code FALHOU} numa tx separada — sem deixar baixa parcial nem
 * contaminar a tx do webhook.
 *
 * <p>Idempotencia: a chave {@code pix:<endToEndId>} garante que replay nao duplica {@code Recebimento}
 * nem credito no escrow (defesas no {@code RegistrarRecebimentoUseCase}); o {@code endToEndId} ausente
 * impede baixa automatica e vira divergencia para a operacao assistida (Task 21.5).
 */
@Service
public class ConciliarRecebimentoPixUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConciliarRecebimentoPixUseCase.class);

    private final PixRecebimentoRepository recebimentoRepository;
    private final PixReferenciaRecebimentoRepository referenciaRepository;
    private final CobrancaRecebimentoPixPort cobrancaPort;

    public ConciliarRecebimentoPixUseCase(
            PixRecebimentoRepository recebimentoRepository,
            PixReferenciaRecebimentoRepository referenciaRepository,
            CobrancaRecebimentoPixPort cobrancaPort) {
        this.recebimentoRepository = recebimentoRepository;
        this.referenciaRepository = referenciaRepository;
        this.cobrancaPort = cobrancaPort;
    }

    /**
     * Baixa a parcela e marca o recebimento {@code CONCILIADO}. Exceptions de cobranca/escrow sobem
     * para o caller (a tx reverte limpa). Idempotente: recebimento fora de {@code EM_PROCESSAMENTO}
     * (ja tratado) e ignorado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void conciliar(UUID recebimentoId) {
        PixRecebimento recebimento = carregarRecebimento(recebimentoId);
        if (recebimento.getStatus() != StatusPixRecebimento.EM_PROCESSAMENTO) {
            log.info(
                    "Recebimento Pix {} ja fora de EM_PROCESSAMENTO ({}) — conciliacao idempotente",
                    recebimentoId,
                    recebimento.getStatus());
            return;
        }
        PixReferenciaRecebimento referencia = carregarReferencia(recebimento.getReferenciaId());

        // A referencia precisa estar ATIVA para auto-baixa. Nao-ATIVA (PAGA/DIVERGENTE/EXPIRADA/
        // CANCELADA) significa txid ja resolvido ou invalido — ex.: o mesmo txid pago por um segundo
        // Pix (endToEndId distinto). Nao baixa de novo: vira divergencia para a operacao assistida
        // (Task 21.5), sem creditar em cima de uma referencia ja encerrada.
        if (referencia.getStatus() != StatusPixReferenciaRecebimento.ATIVA) {
            recebimento.registrarDivergencia(
                    referencia.getId(),
                    referencia.getParcelaId(),
                    "referencia Pix nao esta ATIVA (" + referencia.getStatus() + "): baixa bloqueada");
            recebimentoRepository.save(recebimento);
            return;
        }

        if (recebimento.getEndToEndId() == null || recebimento.getEndToEndId().isBlank()) {
            // Sem endToEndId nao ha chave idempotente segura para a baixa — nao baixa automaticamente.
            recebimento.registrarDivergencia(
                    referencia.getId(), referencia.getParcelaId(), "endToEndId ausente: baixa automatica bloqueada");
            referencia.marcarDivergente();
            persistir(recebimento, referencia);
            return;
        }

        RecebimentoPixCobrancaResult resultado =
                cobrancaPort.registrarRecebimento(new RegistrarRecebimentoPixCobrancaCommand(
                        referencia.getParcelaId(),
                        recebimento.getValor(),
                        recebimento.getRecebidoEm(),
                        "pix:" + recebimento.getEndToEndId(),
                        recebimento.getEndToEndId(),
                        referencia.getTomadorId()));

        recebimento.conciliar(referencia.getId(), referencia.getParcelaId(), resultado.recebimentoCobrancaId());

        // Referencia garantidamente ATIVA aqui (guard acima); transicao terminal direta.
        boolean valorExato = recebimento.getValor().compareTo(referencia.getValorEsperado()) == 0;
        if (valorExato && resultado.parcelaQuitada()) {
            referencia.marcarPaga();
        } else {
            // Parcial (parcela nao quitada) ou valor diferente do esperado: baixa aplicada, mas a
            // referencia fica DIVERGENTE para a operacao assistida tratar (item de backoffice na Task 21.5).
            referencia.marcarDivergente();
        }
        persistir(recebimento, referencia);
    }

    /** Marca o recebimento {@code FALHOU} em tx propria, apos falha de baixa no caller. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarFalha(UUID recebimentoId, String motivo) {
        PixRecebimento recebimento = carregarRecebimento(recebimentoId);
        if (recebimento.getStatus() != StatusPixRecebimento.EM_PROCESSAMENTO) {
            return;
        }
        recebimento.marcarFalhou(motivo);
        recebimentoRepository.save(recebimento);
    }

    private void persistir(PixRecebimento recebimento, PixReferenciaRecebimento referencia) {
        recebimentoRepository.save(recebimento);
        referenciaRepository.save(referencia);
    }

    private PixRecebimento carregarRecebimento(UUID id) {
        return recebimentoRepository
                .findById(id)
                .orElseThrow(() -> new IllegalStateException("PixRecebimento desapareceu na conciliacao: " + id));
    }

    private PixReferenciaRecebimento carregarReferencia(UUID id) {
        return referenciaRepository
                .findById(id)
                .orElseThrow(
                        () -> new IllegalStateException("PixReferenciaRecebimento desapareceu na conciliacao: " + id));
    }
}
