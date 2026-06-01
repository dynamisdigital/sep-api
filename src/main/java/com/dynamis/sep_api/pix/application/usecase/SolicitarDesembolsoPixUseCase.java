package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.SolicitarDesembolsoPixCommand;
import com.dynamis.sep_api.pix.application.dto.SolicitarDesembolsoPixResult;
import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.ContratoDesembolsoView;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaTransferenciaPix;
import com.dynamis.sep_api.pix.application.port.out.exception.PixProviderException;
import com.dynamis.sep_api.pix.application.service.ChavePixSeguranca;
import com.dynamis.sep_api.pix.application.service.DesembolsoTransacaoService;
import com.dynamis.sep_api.pix.application.service.ResultadoElegibilidadeDesembolso;
import com.dynamis.sep_api.pix.application.service.ValidadorElegibilidadeDesembolso;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
import com.dynamis.sep_api.shared.exception.ConflitoException;
import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Solicita um desembolso Pix assistido para um contrato elegivel (Sprint 20 Tasks 20.2/20.3).
 * Persiste a {@link PixTransferencia} em {@link StatusPixTransferencia#CRIADA} (anti-orphan: gravada
 * antes da chamada externa), envia ao {@link PixProvider} e atualiza o status conforme a resposta
 * (SOLICITADA/PROCESSANDO/CONCLUIDA) ou marca {@code FALHOU} em rejeicao/falha tecnica, sempre
 * mantendo a trilha. As escritas ocorrem no {@link DesembolsoTransacaoService} ({@code REQUIRES_NEW})
 * — a transferencia {@code CRIADA} eh comitada antes da chamada externa (anti-orphan real).
 *
 * <p>Step-up estrito ({@code @RequireStepUpEstrito}, sem bypass de MFA) eh aplicado na borda REST
 * (controller de desembolso, Task 20.5) — este use case assume que a autorizacao sensivel ja foi
 * validada, seguindo o padrao do projeto de manter a anotacao de seguranca fora da camada
 * application.
 *
 * <p>Garantias:
 *
 * <ul>
 *   <li><strong>Idempotencia</strong> por {@code Idempotency-Key}: reapresentacao com o mesmo
 *       contrato/valor/chave retorna a transferencia existente; payload divergente -> 409.
 *   <li><strong>Elegibilidade</strong> via {@link ValidadorElegibilidadeDesembolso} (contrato
 *       assinado, agenda ativa, escrow operacional).
 *   <li><strong>Sem duplicidade por contrato</strong>: um contrato com transferencia que o "ocupa"
 *       (CRIADA/SOLICITADA/PROCESSANDO/CONCLUIDA) nao aceita novo desembolso -> 409. A UNIQUE parcial
 *       (V47) fecha a corrida concorrente, traduzida no {@code DesembolsoTransacaoService}.
 *   <li><strong>Minimizacao</strong>: a chave Pix destino nunca eh persistida em claro — apenas hash
 *       (consistencia idempotente) e mascara.
 * </ul>
 */
@Service
public class SolicitarDesembolsoPixUseCase {

    private static final Logger log = LoggerFactory.getLogger(SolicitarDesembolsoPixUseCase.class);

    private static final Collection<StatusPixTransferencia> STATUS_OCUPADOS = Arrays.stream(
                    StatusPixTransferencia.values())
            .filter(StatusPixTransferencia::ocupaContrato)
            .toList();

    private final PixTransferenciaRepository transferenciaRepository;
    private final ValidadorElegibilidadeDesembolso validador;
    private final PixProvider pixProvider;
    private final DesembolsoTransacaoService transacao;

    public SolicitarDesembolsoPixUseCase(
            PixTransferenciaRepository transferenciaRepository,
            ValidadorElegibilidadeDesembolso validador,
            PixProvider pixProvider,
            DesembolsoTransacaoService transacao) {
        this.transferenciaRepository = transferenciaRepository;
        this.validador = validador;
        this.pixProvider = pixProvider;
        this.transacao = transacao;
    }

    /**
     * Orquestra o desembolso SEM transacao ambiente: as fases de escrita ocorrem em
     * {@link DesembolsoTransacaoService} com {@code REQUIRES_NEW}. Assim a transferencia {@code
     * CRIADA} eh <strong>comitada</strong> antes da chamada ao provider (anti-orphan real): se o
     * provider ou a atualizacao posterior falharem, o registro local persiste e fica rastreavel.
     */
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

        // Fase 1: comita CRIADA (anti-orphan). Fase 2: provider + status, em nova tx.
        UUID transferenciaId = transacao.inserirCriada(transferencia, cmd.contratoId());
        PixTransferencia finalizada = enviarAoProvider(transferencia, transferenciaId, cmd);
        return resultado(finalizada, true);
    }

    private PixTransferencia enviarAoProvider(
            PixTransferencia transferencia, UUID transferenciaId, SolicitarDesembolsoPixCommand cmd) {
        try {
            RespostaTransferenciaPix resposta = pixProvider.solicitarTransferencia(
                    new ComandoTransferenciaPix(
                            transferencia.getValor(), cmd.chavePixDestino(), transferencia.getDescricao()),
                    transferencia.getIdempotencyKey(),
                    cmd.correlationId());
            return transacao.aplicarResposta(transferenciaId, resposta);
        } catch (PixProviderException ex) {
            // Falha tecnica: a transferencia ja esta comitada (CRIADA). Marca FALHOU para rastreio e
            // backoffice; o operador reapresenta com nova Idempotency-Key.
            log.warn("Falha tecnica ao solicitar desembolso transferencia={}: {}", transferenciaId, ex.getMessage());
            return transacao.marcarFalha(transferenciaId, "Falha tecnica no provider Pix ao solicitar desembolso");
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
        if (cmd.idempotencyKey().length() > 100) {
            throw new ValidacaoException(
                    "PIX-400-IDEMPOTENCY-KEY-TAMANHO", "Idempotency-Key nao pode exceder 100 caracteres.");
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
