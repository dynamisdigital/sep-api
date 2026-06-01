package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.identity.infrastructure.security.RequireStepUpEstrito;
import com.dynamis.sep_api.pix.application.dto.SolicitarDesembolsoPixCommand;
import com.dynamis.sep_api.pix.application.dto.SolicitarDesembolsoPixResult;
import com.dynamis.sep_api.pix.application.port.out.dto.ContratoDesembolsoView;
import com.dynamis.sep_api.pix.application.service.ChavePixSeguranca;
import com.dynamis.sep_api.pix.application.service.ResultadoElegibilidadeDesembolso;
import com.dynamis.sep_api.pix.application.service.ValidadorElegibilidadeDesembolso;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
import com.dynamis.sep_api.shared.exception.ConflitoException;
import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Solicita um desembolso Pix assistido para um contrato elegivel (Sprint 20 Task 20.2). Nesta task
 * apenas persiste a {@link PixTransferencia} em {@link StatusPixTransferencia#CRIADA}; a chamada ao
 * {@code PixProvider} entra na Task 20.3.
 *
 * <p>Garantias:
 *
 * <ul>
 *   <li><strong>Step-up estrito</strong> ({@link RequireStepUpEstrito}) — sem bypass de MFA.
 *   <li><strong>Idempotencia</strong> por {@code Idempotency-Key}: reapresentacao com o mesmo
 *       contrato/valor/chave retorna a transferencia existente; payload divergente -> 409.
 *   <li><strong>Elegibilidade</strong> via {@link ValidadorElegibilidadeDesembolso} (contrato
 *       assinado, agenda ativa, escrow operacional).
 *   <li><strong>Sem duplicidade por contrato</strong>: um contrato com transferencia que o "ocupa"
 *       (CRIADA/SOLICITADA/PROCESSANDO/CONCLUIDA) nao aceita novo desembolso -> 409. A UNIQUE parcial
 *       (V47) fecha a corrida concorrente, traduzida aqui de {@link DataIntegrityViolationException}.
 *   <li><strong>Minimizacao</strong>: a chave Pix destino nunca eh persistida em claro — apenas hash
 *       (consistencia idempotente) e mascara.
 * </ul>
 */
@Service
public class SolicitarDesembolsoPixUseCase {

    private static final Collection<StatusPixTransferencia> STATUS_OCUPADOS = Arrays.stream(
                    StatusPixTransferencia.values())
            .filter(StatusPixTransferencia::ocupaContrato)
            .toList();

    private final PixTransferenciaRepository transferenciaRepository;
    private final ValidadorElegibilidadeDesembolso validador;

    public SolicitarDesembolsoPixUseCase(
            PixTransferenciaRepository transferenciaRepository, ValidadorElegibilidadeDesembolso validador) {
        this.transferenciaRepository = transferenciaRepository;
        this.validador = validador;
    }

    @Transactional
    @RequireStepUpEstrito
    public SolicitarDesembolsoPixResult executar(SolicitarDesembolsoPixCommand cmd) {
        validarComando(cmd);
        String chaveHash = ChavePixSeguranca.hashHex(cmd.chavePixDestino());

        // Idempotencia: reapresentacao da mesma key retorna a existente (ou 409 se payload diverge).
        Optional<PixTransferencia> existente = transferenciaRepository.findByIdempotencyKey(cmd.idempotencyKey());
        if (existente.isPresent()) {
            return resultadoIdempotente(cmd, chaveHash, existente.get());
        }

        ContratoDesembolsoView contrato = validarElegibilidade(cmd.contratoId());
        validarValorContraContrato(cmd.valor(), contrato);
        bloquearSeContratoOcupado(cmd.contratoId());

        PixTransferencia transferencia = PixTransferencia.criarDesembolso(
                contrato.contratoId(),
                contrato.propostaId(),
                contrato.tomadorId(),
                cmd.valor(),
                chaveHash,
                ChavePixSeguranca.mascarar(cmd.chavePixDestino()),
                cmd.idempotencyKey(),
                cmd.correlationId());

        return persistir(cmd, chaveHash, transferencia);
    }

    private SolicitarDesembolsoPixResult persistir(
            SolicitarDesembolsoPixCommand cmd, String chaveHash, PixTransferencia transferencia) {
        try {
            PixTransferencia salva = transferenciaRepository.saveAndFlush(transferencia);
            return resultado(salva, true);
        } catch (DataIntegrityViolationException corrida) {
            // Corrida concorrente: outra thread persistiu a mesma key (retorno idempotente) ou um
            // desembolso que passou a ocupar o contrato (409). As UNIQUE de V45/V47 sao a fonte da
            // verdade.
            Optional<PixTransferencia> mesmaKey = transferenciaRepository.findByIdempotencyKey(cmd.idempotencyKey());
            if (mesmaKey.isPresent()) {
                return resultadoIdempotente(cmd, chaveHash, mesmaKey.get());
            }
            throw desembolsoDuplicado(cmd.contratoId());
        }
    }

    private SolicitarDesembolsoPixResult resultadoIdempotente(
            SolicitarDesembolsoPixCommand cmd, String chaveHash, PixTransferencia existente) {
        validarConsistenciaIdempotencia(cmd, chaveHash, existente);
        return resultado(existente, false);
    }

    private void validarConsistenciaIdempotencia(
            SolicitarDesembolsoPixCommand cmd, String chaveHash, PixTransferencia existente) {
        boolean mesmoContrato = cmd.contratoId().equals(existente.getContratoId());
        boolean mesmoValor = existente.getValor().compareTo(cmd.valor().setScale(2)) == 0;
        boolean mesmaChave = chaveHash.equals(existente.getChaveDestinoHash());
        if (!mesmoContrato || !mesmoValor || !mesmaChave) {
            throw new ConflitoException(
                    "PIX-409-IDEMPOTENCIA",
                    "Idempotency-Key '" + cmd.idempotencyKey() + "' ja foi usada com contrato/valor/chave diferentes.");
        }
    }

    private void validarComando(SolicitarDesembolsoPixCommand cmd) {
        if (cmd.idempotencyKey() == null || cmd.idempotencyKey().isBlank()) {
            throw new ValidacaoException("PIX-400-IDEMPOTENCY-KEY", "Idempotency-Key obrigatoria.");
        }
        if (cmd.contratoId() == null) {
            throw new ValidacaoException("PIX-400-CONTRATO", "contratoId obrigatorio.");
        }
        if (cmd.chavePixDestino() == null || cmd.chavePixDestino().isBlank()) {
            throw new ValidacaoException("PIX-400-CHAVE", "chave Pix destino obrigatoria.");
        }
        if (cmd.valor() == null || cmd.valor().signum() <= 0) {
            throw new ValidacaoException("PIX-400-VALOR", "valor deve ser positivo.");
        }
        if (cmd.valor().scale() > 2) {
            throw new ValidacaoException("PIX-400-VALOR-ESCALA", "valor nao pode ter mais de 2 casas decimais.");
        }
    }

    private ContratoDesembolsoView validarElegibilidade(UUID contratoId) {
        ResultadoElegibilidadeDesembolso resultado = validador.validar(contratoId);
        if (resultado.elegivel()) {
            return resultado.contrato();
        }
        throw switch (resultado.motivo()) {
            case CONTRATO_NAO_ENCONTRADO -> new RecursoNaoEncontradoException(
                    "PIX-404-CONTRATO", "Contrato nao encontrado para desembolso: " + contratoId);
            case CONTRATO_NAO_ASSINADO -> new OperacaoNaoProcessavelException(
                    "PIX-422-CONTRATO-NAO-ASSINADO", "Contrato nao esta ASSINADO; desembolso indisponivel.");
            case AGENDA_INEXISTENTE -> new OperacaoNaoProcessavelException(
                    "PIX-422-AGENDA-INEXISTENTE", "Contrato sem agenda de cobranca ativa; desembolso indisponivel.");
            case ESCROW_INOPERANTE -> new OperacaoNaoProcessavelException(
                    "PIX-422-ESCROW-INOPERANTE", "Conta escrow da proposta nao esta operacional.");
        };
    }

    private void validarValorContraContrato(BigDecimal valor, ContratoDesembolsoView contrato) {
        if (contrato.valorDesembolso() == null) {
            throw new OperacaoNaoProcessavelException(
                    "PIX-422-VALOR-INDISPONIVEL",
                    "Valor de desembolso indisponivel para o contrato " + contrato.contratoId());
        }
        if (valor.compareTo(contrato.valorDesembolso()) != 0) {
            throw new OperacaoNaoProcessavelException(
                    "PIX-422-VALOR-DIVERGENTE",
                    "Valor informado diverge do valor financiado do contrato " + contrato.contratoId());
        }
    }

    private void bloquearSeContratoOcupado(UUID contratoId) {
        transferenciaRepository
                .findFirstByContratoIdAndStatusInOrderByDataCriacaoDesc(contratoId, STATUS_OCUPADOS)
                .ifPresent(existente -> {
                    throw desembolsoDuplicado(contratoId);
                });
    }

    private ConflitoException desembolsoDuplicado(UUID contratoId) {
        return new ConflitoException(
                "PIX-409-DESEMBOLSO-DUPLICADO",
                "Contrato " + contratoId + " ja possui um desembolso Pix ativo ou concluido.");
    }

    private SolicitarDesembolsoPixResult resultado(PixTransferencia t, boolean novo) {
        return new SolicitarDesembolsoPixResult(
                t.getId(), t.getContratoId(), t.getStatus(), t.getValor(), t.getChaveDestinoMascara(), novo);
    }
}
