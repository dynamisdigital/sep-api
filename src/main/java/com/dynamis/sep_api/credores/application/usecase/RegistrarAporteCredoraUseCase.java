package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.AporteCredoraView;
import com.dynamis.sep_api.credores.application.dto.RegistrarAporteCredoraCommand;
import com.dynamis.sep_api.credores.application.dto.RegistrarAporteCredoraResult;
import com.dynamis.sep_api.credores.application.port.out.AporteEscrowRegistrado;
import com.dynamis.sep_api.credores.application.port.out.ConsultarContratoParaCarteiraCredoraPort;
import com.dynamis.sep_api.credores.application.port.out.ContratoCarteiraView;
import com.dynamis.sep_api.credores.application.port.out.RegistrarAporteEscrowCommand;
import com.dynamis.sep_api.credores.application.port.out.RegistrarAporteEscrowPort;
import com.dynamis.sep_api.credores.domain.event.AporteCredoraRegistradoEvent;
import com.dynamis.sep_api.credores.domain.exception.AporteConflitanteException;
import com.dynamis.sep_api.credores.domain.exception.AporteNaoElegivelException;
import com.dynamis.sep_api.credores.domain.exception.AporteOperacaoNaoEncontradaException;
import com.dynamis.sep_api.credores.domain.model.AporteCredora;
import com.dynamis.sep_api.credores.domain.model.OperacaoFinanciada;
import com.dynamis.sep_api.credores.domain.vo.StatusOperacaoFinanciada;
import com.dynamis.sep_api.credores.infrastructure.persistence.AporteCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OperacaoFinanciadaRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Registro assistido do aporte da credora em operacao financiada da carteira (Sprint 29 Task 29.3).
 * Executado por financeiro/admin — a credora nao inicia o aporte nesta sprint. Step-up estrito fica
 * na borda REST (Task 29.4), padrao do desembolso Pix.
 *
 * <p>Garantias:
 *
 * <ul>
 *   <li><strong>404 neutro</strong>: operacao inexistente lanca excecao generica sem UUID, antes de
 *       revelar qualquer estado.
 *   <li><strong>Idempotencia</strong> por {@code Idempotency-Key} escopada a operacao (UNIQUE V54):
 *       replay com mesmo valor retorna o aporte existente sem novo registro no escrow e sem nova
 *       auditoria; valor divergente -> 409. Checada ANTES da elegibilidade para que replay continue
 *       estavel mesmo se a operacao mudar de estado depois do registro original (padrao Sprint 20).
 *       Requisicoes concorrentes com a mesma chave serializam no {@code SELECT FOR UPDATE} da
 *       operacao — a segunda enxerga o aporte criado e recebe replay 200; o UNIQUE V54 fica como
 *       defesa final, nao como caminho de erro.
 *   <li><strong>Elegibilidade</strong>: operacao ativa na carteira (ASSOCIADA) e contrato {@code
 *       ASSINADO} via porta; caso contrario 409.
 *   <li><strong>Atomicidade local</strong>: escrow desta fase e local (fake) e participa da mesma
 *       transacao — falha no registro do escrow propaga {@code AporteEscrowException} (sanitizada) e
 *       desfaz o aporte; {@code FALHOU} por registro recusado fica reservado a reconciliacao (Task
 *       29.5). Anti-orphan com commit em fase separada so sera necessario com provider externo real
 *       (Fase 5).
 * </ul>
 */
@Service
public class RegistrarAporteCredoraUseCase {

    /** Mesma fronteira da carteira (Sprint 17): somente contrato formalizado aceita aporte. */
    private static final String STATUS_CONTRATO_ELEGIVEL = "ASSINADO";

    private final OperacaoFinanciadaRepository operacaoRepository;
    private final AporteCredoraRepository aporteRepository;
    private final ConsultarContratoParaCarteiraCredoraPort contratoPort;
    private final RegistrarAporteEscrowPort aporteEscrowPort;
    private final ApplicationEventPublisher eventPublisher;

    public RegistrarAporteCredoraUseCase(
            OperacaoFinanciadaRepository operacaoRepository,
            AporteCredoraRepository aporteRepository,
            ConsultarContratoParaCarteiraCredoraPort contratoPort,
            RegistrarAporteEscrowPort aporteEscrowPort,
            ApplicationEventPublisher eventPublisher) {
        this.operacaoRepository = operacaoRepository;
        this.aporteRepository = aporteRepository;
        this.contratoPort = contratoPort;
        this.aporteEscrowPort = aporteEscrowPort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public RegistrarAporteCredoraResult executar(RegistrarAporteCredoraCommand cmd) {
        validarComando(cmd);

        // FOR UPDATE serializa registros concorrentes na mesma operacao: o check de idempotencia
        // abaixo roda sob o lock e a requisicao paralela recebe replay 200, nao violacao do UNIQUE.
        OperacaoFinanciada operacao = operacaoRepository
                .findByIdForUpdate(cmd.operacaoId())
                .orElseThrow(AporteOperacaoNaoEncontradaException::new);

        Optional<AporteCredora> existente =
                aporteRepository.findByOperacaoIdAndIdempotencyKey(operacao.getId(), cmd.idempotencyKey());
        if (existente.isPresent()) {
            return resultadoIdempotente(cmd, existente.get());
        }

        ContratoCarteiraView contrato = validarElegibilidade(operacao);

        // Usa a instancia retornada pelo save (managed): com id UUID atribuido na factory, o save
        // faz merge e mutacoes na instancia original (detached) nao persistiriam.
        AporteCredora aporte = aporteRepository.save(AporteCredora.registrar(
                operacao.getId(), operacao.getEmpresaCredoraId(), cmd.valor(), cmd.idempotencyKey()));

        AporteEscrowRegistrado registro = aporteEscrowPort.registrar(
                new RegistrarAporteEscrowCommand(aporte.getId(), contrato.propostaId(), aporte.getValor()));
        aporte.marcarEmProcessamento(registro.referenciaEscrow());

        eventPublisher.publishEvent(new AporteCredoraRegistradoEvent(
                aporte.getId(), operacao.getId(), operacao.getEmpresaCredoraId(), aporte.getValor(), cmd.atorId()));
        return new RegistrarAporteCredoraResult(AporteCredoraView.de(aporte), true);
    }

    private RegistrarAporteCredoraResult resultadoIdempotente(
            RegistrarAporteCredoraCommand cmd, AporteCredora existente) {
        boolean mesmoValor = existente.getValor().compareTo(cmd.valor().setScale(2)) == 0;
        if (!mesmoValor) {
            throw new AporteConflitanteException();
        }
        return new RegistrarAporteCredoraResult(AporteCredoraView.de(existente), false);
    }

    private ContratoCarteiraView validarElegibilidade(OperacaoFinanciada operacao) {
        if (operacao.getStatus() != StatusOperacaoFinanciada.ASSOCIADA) {
            throw new AporteNaoElegivelException();
        }
        ContratoCarteiraView contrato =
                contratoPort.consultarPorId(operacao.getContratoId()).orElseThrow(AporteNaoElegivelException::new);
        if (!STATUS_CONTRATO_ELEGIVEL.equals(contrato.status())) {
            throw new AporteNaoElegivelException();
        }
        return contrato;
    }

    private void validarComando(RegistrarAporteCredoraCommand cmd) {
        if (cmd.operacaoId() == null) {
            throw new ValidacaoException("CRD-400-002", "operacaoId obrigatorio.");
        }
        if (cmd.idempotencyKey() == null || cmd.idempotencyKey().isBlank()) {
            throw new ValidacaoException("CRD-400-003", "Idempotency-Key obrigatoria.");
        }
        if (cmd.idempotencyKey().length() > 100) {
            throw new ValidacaoException("CRD-400-004", "Idempotency-Key nao pode exceder 100 caracteres.");
        }
        if (cmd.valor() == null || cmd.valor().signum() <= 0) {
            throw new ValidacaoException("CRD-400-005", "valor deve ser positivo.");
        }
        if (cmd.valor().scale() > 2) {
            throw new ValidacaoException("CRD-400-006", "valor nao pode ter mais de 2 casas decimais.");
        }
    }
}
