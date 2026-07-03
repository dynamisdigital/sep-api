package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.InteresseView;
import com.dynamis.sep_api.credores.domain.exception.InteresseNaoEncontradoException;
import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import com.dynamis.sep_api.credores.domain.model.InteresseCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusInteresseCredora;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.InteresseCredoraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consulta o interesse ATIVO da credora do usuario autenticado numa oportunidade (Sprint 25, Gate I1
 * da M-10). Leitura autoritativa que permite ao mobile distinguir manifestar de cancelar apos reload
 * ou novo login. Read-only: sem evento, mutacao ou step-up.
 *
 * <p>404 neutro (anti-enumeracao): usuario sem credora e credora sem interesse ativo retornam a
 * mesma {@link InteresseNaoEncontradoException} — mesmo corpo, sem vazar o {@code usuarioId} — para
 * nao permitir distinguir os dois casos.
 */
@Service
public class ConsultarInteresseAtivoCredoraUseCase {

    private final EmpresaCredoraRepository empresaRepository;
    private final InteresseCredoraRepository interesseRepository;

    public ConsultarInteresseAtivoCredoraUseCase(
            EmpresaCredoraRepository empresaRepository, InteresseCredoraRepository interesseRepository) {
        this.empresaRepository = empresaRepository;
        this.interesseRepository = interesseRepository;
    }

    @Transactional(readOnly = true)
    public InteresseView executar(UUID usuarioId, UUID oportunidadeId) {
        EmpresaCredora credora = empresaRepository.findByUsuarioId(usuarioId).orElse(null);
        if (credora == null) {
            throw new InteresseNaoEncontradoException();
        }

        InteresseCredora interesse = interesseRepository
                .findByEmpresaCredoraIdAndOportunidadeIdAndStatus(
                        credora.getId(), oportunidadeId, StatusInteresseCredora.ATIVO)
                .orElseThrow(InteresseNaoEncontradoException::new);

        return InteresseView.de(interesse);
    }
}
