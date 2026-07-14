package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.CadastrarChavePixCommand;
import com.dynamis.sep_api.pix.application.dto.CadastrarChavePixResult;
import com.dynamis.sep_api.pix.application.port.out.ContaOperacionalEscrowQueryPort;
import com.dynamis.sep_api.pix.application.port.out.PixProvider;
import com.dynamis.sep_api.pix.application.port.out.dto.ComandoCadastrarChavePix;
import com.dynamis.sep_api.pix.application.port.out.dto.ContaOperacionalEscrowView;
import com.dynamis.sep_api.pix.application.port.out.dto.RespostaCadastroChavePix;
import com.dynamis.sep_api.pix.application.service.ChavePixSeguranca;
import com.dynamis.sep_api.pix.application.service.NormalizadorChavePix;
import com.dynamis.sep_api.pix.domain.event.PixChaveCadastradaEvent;
import com.dynamis.sep_api.pix.domain.model.ChavePix;
import com.dynamis.sep_api.pix.domain.vo.StatusChavePix;
import com.dynamis.sep_api.pix.infrastructure.persistence.ChavePixRepository;
import com.dynamis.sep_api.shared.exception.ConflitoException;
import com.dynamis.sep_api.shared.exception.OperacaoNaoProcessavelException;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Cadastro assistido de chave Pix da conta operacional/escrow (Sprint 31 Task 31.5). Step-up
 * estrito e roles ficam na borda REST (Task 31.7), padrao do projeto.
 *
 * <p>Fluxo: valida {@code Idempotency-Key}, normaliza tipo/valor ({@link NormalizadorChavePix}),
 * resolve a conta operacional por porta, verifica replay e duplicata ativa, chama o provider com a
 * <strong>mesma</strong> idempotency key e so entao persiste — falha externa nao cria chave ATIVA
 * nem auditoria. O provider honra idempotencia, permitindo retry seguro caso a chamada externa
 * conclua e a persistencia local falhe (sem compensacao automatica).
 *
 * <p>Garantias:
 *
 * <ul>
 *   <li><strong>Idempotencia</strong>: replay com mesmo tipo/valor/conta retorna a chave existente
 *       ({@code novo=false}), sem provider nem auditoria adicionais; payload divergente -> 409.
 *   <li><strong>Unicidade ativa</strong>: chave equivalente ja ATIVA na conta -> 409; requisicoes
 *       concorrentes serializam por advisory lock em (conta, tipo, hash) antes da chamada externa
 *       — o perdedor nao chama o provider (sem chave externa orfa); as UNIQUEs da V58 seguem como
 *       defesa residual, convergidas para replay ou 409, nunca 500.
 *   <li><strong>Minimizacao</strong>: o valor em claro existe apenas no comando em memoria ate a
 *       chamada ao provider; persistem-se hash + mascara.
 *   <li><strong>Auditoria</strong>: {@link PixChaveCadastradaEvent} publicado dentro da transacao
 *       de escrita, somente na criacao nova (listener AFTER_COMMIT).
 * </ul>
 */
@Service
public class CadastrarChavePixUseCase {

    private final ChavePixRepository repository;
    private final ContaOperacionalEscrowQueryPort contaPort;
    private final PixProvider pixProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final TransactionTemplate transacao;

    public CadastrarChavePixUseCase(
            ChavePixRepository repository,
            ContaOperacionalEscrowQueryPort contaPort,
            PixProvider pixProvider,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            PlatformTransactionManager txManager) {
        this.repository = repository;
        this.contaPort = contaPort;
        this.pixProvider = pixProvider;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.transacao = new TransactionTemplate(txManager);
    }

    /**
     * Orquestra em transacao unica serializada por advisory lock transacional em (conta, tipo,
     * hash) — review manual Sprint 31 (P1): o lock e adquirido ANTES das verificacoes e da chamada
     * externa, entao a segunda requisicao concorrente com o mesmo valor so re-checa apos o commit
     * da vencedora e leva 409/replay <strong>sem chamar o provider</strong> (nenhuma chave externa
     * orfa). A chamada HTTP dentro da transacao e o mesmo tradeoff documentado da remocao
     * (operacao assistida de baixo volume). A convergencia por constraint permanece apenas como
     * defesa residual.
     */
    public CadastrarChavePixResult executar(CadastrarChavePixCommand cmd) {
        validarIdempotencyKey(cmd.idempotencyKey());
        String valorNormalizado = NormalizadorChavePix.normalizar(cmd.tipo(), cmd.valor());
        ContaOperacionalEscrowView conta = resolverContaOperacional();
        String valorHash = ChavePixSeguranca.hashHex(valorNormalizado);

        try {
            return transacao.execute(status -> cadastrarSerializado(cmd, conta, valorNormalizado, valorHash));
        } catch (DataIntegrityViolationException ex) {
            return convergirCorrida(cmd, conta, valorHash, ex);
        }
    }

    private CadastrarChavePixResult cadastrarSerializado(
            CadastrarChavePixCommand cmd, ContaOperacionalEscrowView conta, String valorNormalizado, String valorHash) {
        repository.travarCadastroChave(chaveDeTrava(conta.contaEscrowId(), cmd, valorHash));

        Optional<ChavePix> replay =
                repository.findByContaEscrowIdAndIdempotencyKey(conta.contaEscrowId(), cmd.idempotencyKey());
        if (replay.isPresent()) {
            return resultadoIdempotente(cmd, valorHash, replay.get());
        }
        bloquearDuplicataAtiva(conta.contaEscrowId(), cmd, valorHash);

        RespostaCadastroChavePix resposta = pixProvider.cadastrarChave(
                new ComandoCadastrarChavePix(cmd.tipo(), valorNormalizado, conta.contaTecnicaId()),
                cmd.idempotencyKey(),
                cmd.correlationId());

        ChavePix chave = ChavePix.cadastrar(
                conta.contaEscrowId(),
                cmd.tipo(),
                valorHash,
                ChavePixSeguranca.mascarar(valorNormalizado),
                resposta.providerKeyId(),
                cmd.idempotencyKey(),
                cmd.operadorId(),
                OffsetDateTime.now(clock));

        ChavePix persistida = repository.saveAndFlush(chave);
        eventPublisher.publishEvent(new PixChaveCadastradaEvent(
                persistida.getId(), persistida.getContaEscrowId(), persistida.getTipo(), cmd.operadorId()));
        return resultado(persistida, true);
    }

    /**
     * Deriva a chave do advisory lock de (conta, tipo, hash do valor normalizado) — deterministico:
     * requisicoes concorrentes da mesma chave Pix disputam o mesmo lock. Colisao de 64 bits apenas
     * serializa a mais; nunca afeta correcao.
     */
    private static long chaveDeTrava(UUID contaEscrowId, CadastrarChavePixCommand cmd, String valorHash) {
        String hex = ChavePixSeguranca.hashHex(contaEscrowId + "|" + cmd.tipo() + "|" + valorHash);
        return Long.parseUnsignedLong(hex.substring(0, 16), 16);
    }

    /**
     * Defesa residual: corrida que ainda escape do advisory lock cai nas UNIQUEs da V58; re-le o
     * vencedor e converge para replay idempotente (mesma key) ou conflito funcional (chave ativa
     * equivalente). Constraint desconhecida propaga.
     */
    private CadastrarChavePixResult convergirCorrida(
            CadastrarChavePixCommand cmd,
            ContaOperacionalEscrowView conta,
            String valorHash,
            DataIntegrityViolationException ex) {
        Optional<ChavePix> replay =
                repository.findByContaEscrowIdAndIdempotencyKey(conta.contaEscrowId(), cmd.idempotencyKey());
        if (replay.isPresent()) {
            return resultadoIdempotente(cmd, valorHash, replay.get());
        }
        bloquearDuplicataAtiva(conta.contaEscrowId(), cmd, valorHash);
        throw ex;
    }

    private CadastrarChavePixResult resultadoIdempotente(
            CadastrarChavePixCommand cmd, String valorHash, ChavePix existente) {
        boolean mesmoTipo = existente.getTipo() == cmd.tipo();
        boolean mesmoValor = existente.getValorHash().equals(valorHash);
        if (!mesmoTipo || !mesmoValor) {
            throw new ConflitoException(
                    "PIX-409-IDEMPOTENCIA-CHAVE",
                    "Idempotency-Key '" + cmd.idempotencyKey() + "' ja foi usada com tipo/valor de chave diferentes.");
        }
        return resultado(existente, false);
    }

    private void bloquearDuplicataAtiva(UUID contaEscrowId, CadastrarChavePixCommand cmd, String valorHash) {
        repository
                .findByContaEscrowIdAndTipoAndValorHashAndStatus(
                        contaEscrowId, cmd.tipo(), valorHash, StatusChavePix.ATIVA)
                .ifPresent(ativa -> {
                    throw new ConflitoException(
                            "PIX-409-CHAVE-ATIVA", "Ja existe chave Pix ativa equivalente na conta operacional.");
                });
    }

    private ContaOperacionalEscrowView resolverContaOperacional() {
        return contaPort
                .buscarContaOperacionalAtiva()
                .orElseThrow(() -> new OperacaoNaoProcessavelException(
                        "PIX-422-CONTA-OPERACIONAL",
                        "Conta operacional/escrow indisponivel para gestao de chaves Pix."));
    }

    private void validarIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidacaoException("PIX-400-IDEMPOTENCY-KEY", "Idempotency-Key obrigatoria.");
        }
        if (idempotencyKey.length() > 100) {
            throw new ValidacaoException(
                    "PIX-400-IDEMPOTENCY-KEY-TAMANHO", "Idempotency-Key nao pode exceder 100 caracteres.");
        }
    }

    private CadastrarChavePixResult resultado(ChavePix chave, boolean novo) {
        return new CadastrarChavePixResult(
                chave.getId(),
                chave.getTipo(),
                chave.getValorMascarado(),
                chave.getStatus(),
                chave.getCriadaEm(),
                chave.getRemovidaEm(),
                novo);
    }
}
