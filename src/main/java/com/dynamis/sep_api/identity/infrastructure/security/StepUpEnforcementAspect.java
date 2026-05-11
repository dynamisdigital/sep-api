package com.dynamis.sep_api.identity.infrastructure.security;

import com.dynamis.sep_api.identity.application.service.StepUpTokenService;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

/**
 * Aplica {@link RequireStepUp} (Sprint 5 Task 5.6): exige header {@code X-Step-Up-Token} valido e
 * pertencente ao usuario autenticado. Em falha, lanca {@link AccessDeniedException} (403).
 *
 * <p>Bypass: se o usuario ainda nao habilitou MFA, step-up nao pode ser produzido (depende de TOTP
 * ativo); para nao bloquear migracao de usuarios pre-MFA, o aspect permite a chamada quando
 * {@code mfaHabilitado=false}. Apos Task 5.10 (migracao) todos os usuarios terao MFA.
 */
@Aspect
@Component
public class StepUpEnforcementAspect {

    public static final String HEADER = "X-Step-Up-Token";

    private final StepUpTokenService tokenService;
    private final UsuarioRepository usuarioRepository;

    public StepUpEnforcementAspect(StepUpTokenService tokenService, UsuarioRepository usuarioRepository) {
        this.tokenService = tokenService;
        this.usuarioRepository = usuarioRepository;
    }

    @Before("@annotation(com.dynamis.sep_api.identity.infrastructure.security.RequireStepUp)")
    public void aplicar() {
        UUID autenticadoId = principalId();
        if (autenticadoId == null) {
            throw new AccessDeniedException("Autenticacao requerida para operacao sensivel.");
        }
        Optional<Usuario> usuario = usuarioRepository.findById(autenticadoId);
        if (usuario.isPresent() && !usuario.get().isMfaHabilitado()) {
            return;
        }
        HttpServletRequest request = requestAtual();
        String tokenCru = request.getHeader(HEADER);
        Optional<UUID> tokenUsuarioId = tokenService.validarEConsumir(tokenCru);
        if (tokenUsuarioId.isEmpty()) {
            throw new AccessDeniedException("Step-up token ausente ou invalido.");
        }
        if (!autenticadoId.equals(tokenUsuarioId.get())) {
            throw new AccessDeniedException("Step-up token nao pertence ao usuario autenticado.");
        }
    }

    private HttpServletRequest requestAtual() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attrs.getRequest();
    }

    private UUID principalId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u) {
            return u.id();
        }
        return null;
    }
}
