package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.GerarReferenciaRecebimentoPixCommand;
import com.dynamis.sep_api.pix.application.dto.GerarReferenciaRecebimentoPixResult;
import com.dynamis.sep_api.pix.application.port.out.CobrancaRecebimentoPixQueryPort;
import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoCriarCobrancaPix;
import com.dynamis.sep_api.pix.application.port.out.dto.ParcelaRecebimentoPixView;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaCobrancaPix;
import com.dynamis.sep_api.pix.domain.model.PixReferenciaRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixReferenciaRecebimentoRepository;
import com.dynamis.sep_api.shared.exception.ConflitoException;
import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;
import com.dynamis.sep_api.shared.exception.RecursoNaoEncontradoException;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import com.fasterxml.uuid.Generators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Gera (ou reaproveita) uma referencia Pix de recebimento para uma parcela elegivel (Sprint 21 Task
 * 21.2). O {@code txid} eh controlado pelo SEP e enviado ao {@link PixProvider} para que o webhook
 * {@code RECEBIMENTO_PIX} (Task 21.3) correlacione o pagamento de volta a parcela.
 *
 * <p>Garantias:
 *
 * <ul>
 *   <li><strong>Isolamento DDD</strong>: a parcela eh lida via {@link CobrancaRecebimentoPixQueryPort}
 *       — o {@code pix} nunca toca entidades/repositorios de {@code cobranca}. O valor esperado eh o
 *       valor em aberto calculado por {@code cobranca}, sem recalculo aqui.
 *   <li><strong>Idempotencia por parcela</strong>: no maximo uma referencia {@code ATIVA} por
 *       parcela. Reapresentacao retorna a existente ({@code novo=false}).
 *   <li><strong>Anti-orphan</strong>: a referencia {@code ATIVA} eh inserida (com flush) ANTES da
 *       chamada ao provider — a corrida concorrente cai na UNIQUE parcial e vira 409 sem cobranca
 *       orfa no provider; se o provider falhar, a transacao inteira faz rollback (nenhuma referencia
 *       fica pendurada).
 *   <li><strong>Minimizacao</strong>: persiste apenas ids/txid/valor/copia-cola — sem dado pessoal
 *       ou bancario.
 * </ul>
 */
@Service
public class GerarReferenciaRecebimentoPixUseCase {

    private static final Logger log = LoggerFactory.getLogger(GerarReferenciaRecebimentoPixUseCase.class);
    private static final String DESCRICAO_COBRANCA = "Recebimento de parcela SEP";

    private final CobrancaRecebimentoPixQueryPort cobrancaQueryPort;
    private final PixReferenciaRecebimentoRepository referenciaRepository;
    private final PixProvider pixProvider;

    public GerarReferenciaRecebimentoPixUseCase(
            CobrancaRecebimentoPixQueryPort cobrancaQueryPort,
            PixReferenciaRecebimentoRepository referenciaRepository,
            PixProvider pixProvider) {
        this.cobrancaQueryPort = cobrancaQueryPort;
        this.referenciaRepository = referenciaRepository;
        this.pixProvider = pixProvider;
    }

    @Transactional
    public GerarReferenciaRecebimentoPixResult executar(GerarReferenciaRecebimentoPixCommand cmd) {
        if (cmd.parcelaId() == null) {
            throw new ValidacaoException("PIX-400-PARCELA", "parcelaId obrigatorio.");
        }

        ParcelaRecebimentoPixView parcela = cobrancaQueryPort
                .buscarParcelaParaReferenciaPix(cmd.parcelaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "PIX-404-PARCELA", "Parcela nao encontrada para recebimento Pix: " + cmd.parcelaId()));

        if (!parcela.permiteRecebimento()) {
            throw new OperacaoNaoProcessavelException(
                    "PIX-422-PARCELA-NAO-RECEBIVEL", "Parcela nao permite recebimento no estado atual.");
        }
        if (parcela.valorEmAberto().signum() <= 0) {
            throw new OperacaoNaoProcessavelException(
                    "PIX-422-PARCELA-SEM-SALDO", "Parcela sem valor em aberto para gerar cobranca Pix.");
        }

        Optional<PixReferenciaRecebimento> ativa =
                referenciaRepository.findByParcelaIdAndStatus(cmd.parcelaId(), StatusPixReferenciaRecebimento.ATIVA);
        if (ativa.isPresent()) {
            return resultado(ativa.get(), false);
        }

        PixReferenciaRecebimento referencia = persistirReferenciaAtiva(parcela, cmd.correlationId());
        RespostaCobrancaPix resposta = criarCobrancaNoProvider(referencia, parcela, cmd.correlationId());
        referencia.vincularProvider(resposta.providerReferenciaId(), resposta.codigoCopiaCola());
        referenciaRepository.save(referencia);
        return resultado(referencia, true);
    }

    private PixReferenciaRecebimento persistirReferenciaAtiva(ParcelaRecebimentoPixView parcela, String correlationId) {
        PixReferenciaRecebimento referencia = PixReferenciaRecebimento.criar(
                parcela.parcelaId(),
                parcela.contratoId(),
                parcela.tomadorId(),
                parcela.valorEmAberto(),
                gerarTxid(),
                correlationId);
        try {
            // Flush imediato: a corrida concorrente bate na UNIQUE parcial (1 ATIVA por parcela)
            // ANTES da chamada ao provider — sem cobranca orfa. Tx fica rollback-only, devolve 409.
            return referenciaRepository.saveAndFlush(referencia);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflitoException(
                    "PIX-409-REFERENCIA-CONCORRENTE",
                    "Ja existe geracao de referencia Pix em andamento para a parcela " + parcela.parcelaId());
        }
    }

    private RespostaCobrancaPix criarCobrancaNoProvider(
            PixReferenciaRecebimento referencia, ParcelaRecebimentoPixView parcela, String correlationId) {
        // Falha tecnica do provider sobe como PixProviderException -> rollback da tx (a referencia
        // recem-inserida nao eh comitada) -> ApiExceptionHandler traduz para 502/503.
        return pixProvider.criarCobrancaRecebimento(
                new ComandoCriarCobrancaPix(referencia.getTxid(), parcela.valorEmAberto(), DESCRICAO_COBRANCA),
                correlationId);
    }

    /** txid deterministico controlado pelo SEP — UUID v6 em hex (32 chars), sem dado sensivel. */
    private String gerarTxid() {
        return Generators.timeBasedReorderedGenerator().generate().toString().replace("-", "");
    }

    private GerarReferenciaRecebimentoPixResult resultado(PixReferenciaRecebimento referencia, boolean novo) {
        if (log.isDebugEnabled()) {
            log.debug(
                    "Referencia Pix recebimento id={} parcela={} novo={}",
                    referencia.getId(),
                    referencia.getParcelaId(),
                    novo);
        }
        return new GerarReferenciaRecebimentoPixResult(
                referencia.getId(),
                referencia.getParcelaId(),
                referencia.getTxid(),
                referencia.getCodigoCopiaCola(),
                referencia.getValorEsperado(),
                referencia.getStatus(),
                novo);
    }
}
