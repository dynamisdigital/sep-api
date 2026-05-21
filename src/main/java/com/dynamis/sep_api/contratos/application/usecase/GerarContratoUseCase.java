package com.dynamis.sep_api.contratos.application.usecase;

import com.dynamis.sep_api.contratos.application.port.out.TemplateContratoEngine;
import com.dynamis.sep_api.contratos.application.port.out.dto.TemplateContratoRequest;
import com.dynamis.sep_api.contratos.application.port.out.dto.TemplateContratoResponse;
import com.dynamis.sep_api.contratos.application.service.ContextoContratoBuilder;
import com.dynamis.sep_api.contratos.application.service.HashContratoService;
import com.dynamis.sep_api.contratos.application.usecase.command.GerarContratoCommand;
import com.dynamis.sep_api.contratos.domain.event.ContratoGeradoEvent;
import com.dynamis.sep_api.contratos.domain.event.ContratoNovaVersaoEvent;
import com.dynamis.sep_api.contratos.domain.exception.ContratoEstadoInvalidoException;
import com.dynamis.sep_api.contratos.domain.exception.PropostaNaoAprovadaException;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.model.VersaoContrato;
import com.dynamis.sep_api.contratos.domain.vo.TipoContrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import com.dynamis.sep_api.credito.application.usecase.ConsultarPropostaUseCase;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Gera contrato a partir de proposta de credito {@link StatusProposta#APROVADA} (Sprint 10 Task
 * 10.3). Suporta criacao inicial, re-geracao pre-aceite e idempotencia por
 * {@code parecerOrigemId}.
 *
 * <p>Regras:
 *
 * <ul>
 *   <li>Proposta deve existir; senao {@link
 *       com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException} (404 via
 *       {@link ConsultarPropostaUseCase}).
 *   <li>Proposta deve estar APROVADA; senao {@link PropostaNaoAprovadaException} (422).
 *   <li>Idempotencia: se ja existe versao vigente do contrato com o mesmo
 *       {@code parecerOrigemId} do comando (replay do evento), retorna o contrato sem criar nova
 *       versao.
 *   <li>Sem contrato: cria {@link Contrato} em GERADO + versao numero 1 + clausulas, transiciona
 *       para AGUARDANDO_ACEITE e publica {@link ContratoGeradoEvent}.
 *   <li>Contrato em GERADO/AGUARDANDO_ACEITE com parecer diferente (ou comando manual sem
 *       parecer): adiciona versao numero+1, publica {@link ContratoNovaVersaoEvent}.
 *   <li>Contrato em ACEITO/EM_ASSINATURA/ASSINADO/CANCELADO: rejeita com
 *       {@link ContratoEstadoInvalidoException} (409) antes de renderizar template (fail-fast).
 * </ul>
 *
 * <p>Acessa o modulo {@code credito} apenas via {@link ConsultarPropostaUseCase} (porta da
 * application layer), respeitando a regra arquitetural de nao acessar repository interno de
 * outro modulo. Sprint 10 trata todos os tipos de operacao como {@link TipoContrato#MUTUO} (CCB
 * entra na Sprint 11).
 */
@Service
public class GerarContratoUseCase {

    private static final Logger log = LoggerFactory.getLogger(GerarContratoUseCase.class);

    private final ConsultarPropostaUseCase consultarPropostaUseCase;
    private final ContratoRepository contratoRepository;
    private final ContextoContratoBuilder contextoBuilder;
    private final TemplateContratoEngine templateEngine;
    private final HashContratoService hashService;
    private final ApplicationEventPublisher eventPublisher;

    public GerarContratoUseCase(
            ConsultarPropostaUseCase consultarPropostaUseCase,
            ContratoRepository contratoRepository,
            ContextoContratoBuilder contextoBuilder,
            TemplateContratoEngine templateEngine,
            HashContratoService hashService,
            ApplicationEventPublisher eventPublisher) {
        this.consultarPropostaUseCase = consultarPropostaUseCase;
        this.contratoRepository = contratoRepository;
        this.contextoBuilder = contextoBuilder;
        this.templateEngine = templateEngine;
        this.hashService = hashService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Contrato executar(GerarContratoCommand command) {
        PropostaCredito proposta = consultarPropostaUseCase.executar(command.propostaId());

        if (proposta.getStatus() != StatusProposta.APROVADA) {
            throw new PropostaNaoAprovadaException(proposta.getId(), proposta.getStatus());
        }

        Contrato contrato =
                contratoRepository.findByPropostaId(proposta.getId()).orElse(null);

        // Idempotencia: se vigente ja foi gerada pelo mesmo parecer (replay do evento), short-circuit.
        if (contrato != null && command.parecerOrigemId() != null) {
            VersaoContrato vigente = contrato.versaoVigente().orElse(null);
            if (vigente != null && Objects.equals(vigente.getParecerOrigemId(), command.parecerOrigemId())) {
                log.info(
                        "Geracao idempotente — contrato {} ja tem versao {} gerada pelo parecer {}; pulando.",
                        contrato.getId(),
                        vigente.getNumero(),
                        command.parecerOrigemId());
                return contrato;
            }
        }

        if (contrato == null) {
            contrato = Contrato.criar(proposta.getId(), proposta.getTomadorId(), TipoContrato.MUTUO);
        }

        // Valida estado antes de renderizar (evita custo de template quando contrato ja em ACEITO+).
        if (!contrato.getStatus().permiteNovaVersao()) {
            throw new ContratoEstadoInvalidoException("adicionarVersao", contrato.getStatus());
        }

        boolean primeiraVersao = contrato.getVersoes().isEmpty();

        TemplateContratoRequest req =
                new TemplateContratoRequest(contrato.getTipo(), contextoBuilder.construir(proposta));
        TemplateContratoResponse resp = templateEngine.renderizar(req);
        String hash = hashService.calcular(resp.conteudoTexto());

        VersaoContrato versao = contrato.adicionarVersao(resp.conteudoTexto(), hash, command.parecerOrigemId());
        resp.clausulas().forEach(c -> versao.adicionarClausula(c.ordem(), c.titulo(), c.texto()));

        Contrato salvo = contratoRepository.save(contrato);

        if (primeiraVersao) {
            log.info(
                    "Contrato gerado: contratoId={} propostaId={} versao={}",
                    salvo.getId(),
                    salvo.getPropostaId(),
                    versao.getNumero());
            eventPublisher.publishEvent(new ContratoGeradoEvent(
                    salvo.getId(),
                    salvo.getPropostaId(),
                    salvo.getTomadorId(),
                    versao.getId(),
                    versao.getNumero(),
                    versao.getHashSha256()));
        } else {
            log.info(
                    "Contrato regenerado: contratoId={} propostaId={} novaVersao={}",
                    salvo.getId(),
                    salvo.getPropostaId(),
                    versao.getNumero());
            eventPublisher.publishEvent(new ContratoNovaVersaoEvent(
                    salvo.getId(),
                    salvo.getPropostaId(),
                    salvo.getTomadorId(),
                    versao.getId(),
                    versao.getNumero(),
                    versao.getHashSha256()));
        }
        return salvo;
    }
}
