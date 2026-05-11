package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.exception.MfaNaoHabilitadoException;
import com.dynamis.sep_api.identity.application.service.StepUpChallengeService;
import com.dynamis.sep_api.identity.domain.model.MfaStatus;
import com.dynamis.sep_api.identity.domain.model.UsuarioTotpSecret;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioTotpSecretRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Inicia step-up: usuario autenticado pede challenge para reautenticar antes de operacao sensivel
 * (Sprint 5 Task 5.6). Pre-condicao: MFA TOTP ativo. Sem MFA, step-up nao faz sentido.
 */
@Service
public class IniciarStepUpUseCase {

    private final UsuarioTotpSecretRepository totpRepository;
    private final StepUpChallengeService challengeService;

    public IniciarStepUpUseCase(UsuarioTotpSecretRepository totpRepository, StepUpChallengeService challengeService) {
        this.totpRepository = totpRepository;
        this.challengeService = challengeService;
    }

    @Transactional(readOnly = true)
    public UUID executar(UUID usuarioId) {
        UsuarioTotpSecret secret =
                totpRepository.findByUsuarioId(usuarioId).orElseThrow(MfaNaoHabilitadoException::new);
        if (secret.getStatus() != MfaStatus.ATIVO) {
            throw new MfaNaoHabilitadoException();
        }
        return challengeService.iniciar(usuarioId);
    }
}
