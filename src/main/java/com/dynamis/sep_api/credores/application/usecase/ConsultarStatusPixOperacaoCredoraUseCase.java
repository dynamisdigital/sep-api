package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.PixOperacaoStatusView;
import com.dynamis.sep_api.credores.application.port.out.PixOperacaoStatusQueryPort;
import com.dynamis.sep_api.credores.domain.exception.StatusPixOperacaoNaoEncontradoException;
import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import com.dynamis.sep_api.credores.domain.model.OperacaoFinanciada;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.OperacaoFinanciadaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consulta owner-scoped do status Pix de uma operacao da carteira da credora do usuario autenticado
 * (Sprint 26 — Gate P3). Resolve a credora pelo usuario e valida a posse da operacao ANTES de
 * consultar o Pix. Sem role {@code CREDORA}: o acesso e por autenticacao + presenca de credora.
 * Usuario sem credora, operacao de outra credora, operacao inexistente e operacao sem desembolso Pix
 * lancam a mesma {@link StatusPixOperacaoNaoEncontradoException} (404 neutro, sem UUID). Leitura
 * local, sem provider, conciliacao ou step-up.
 */
@Service
@Transactional(readOnly = true)
public class ConsultarStatusPixOperacaoCredoraUseCase {

    private final EmpresaCredoraRepository empresaRepository;
    private final OperacaoFinanciadaRepository operacaoRepository;
    private final PixOperacaoStatusQueryPort pixOperacaoStatusPort;

    public ConsultarStatusPixOperacaoCredoraUseCase(
            EmpresaCredoraRepository empresaRepository,
            OperacaoFinanciadaRepository operacaoRepository,
            PixOperacaoStatusQueryPort pixOperacaoStatusPort) {
        this.empresaRepository = empresaRepository;
        this.operacaoRepository = operacaoRepository;
        this.pixOperacaoStatusPort = pixOperacaoStatusPort;
    }

    public PixOperacaoStatusView executar(UUID usuarioId, UUID operacaoId) {
        EmpresaCredora credora =
                empresaRepository.findByUsuarioId(usuarioId).orElseThrow(StatusPixOperacaoNaoEncontradoException::new);
        OperacaoFinanciada operacao = operacaoRepository
                .findByIdAndEmpresaCredoraId(operacaoId, credora.getId())
                .orElseThrow(StatusPixOperacaoNaoEncontradoException::new);
        return pixOperacaoStatusPort
                .consultarPorContrato(operacao.getContratoId())
                .orElseThrow(StatusPixOperacaoNaoEncontradoException::new);
    }
}
