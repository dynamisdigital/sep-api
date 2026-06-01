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
 * Aplica os marcadores de step-up em operacoes sensiveis: exige header {@code X-Step-Up-Token}
 * valido e pertencente ao usuario autenticado. Em falha, lanca {@link AccessDeniedException} (403).
 *
 * <ul>
 *   <li>{@link RequireStepUp} (Sprint 5 Task 5.6) — com <strong>bypass</strong>: se o usuario ainda
 *       nao habilitou MFA, step-up nao pode ser produzido (depende de TOTP ativo); para nao
 *       bloquear migracao de usuarios pre-MFA, a chamada eh permitida quando
 *       {@code mfaHabilitado=false}.
 *   <li>{@link RequireStepUpEstrito} (Sprint 20) — <strong>sem bypass</strong>: exige token valido
 *       independentemente de MFA. Usado em operacoes financeiras de alto risco (desembolso Pix), onde
 *       liberar operador sem MFA seria inaceitavel.
 * </ul>
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
        UUID autenticadoId = exigirAutenticado();
        Optional<Usuario> usuario = usuarioRepository.findById(autenticadoId);
        if (usuario.isPresent() && !usuario.get().isMfaHabilitado()) {
            // Bypass de migracao pre-MFA (Sprint 5).
            return;
        }
        exigirStepUpValido(autenticadoId);
    }

    @Before("@annotation(com.dynamis.sep_api.identity.infrastructure.security.RequireStepUpEstrito)")
    public void aplicarEstrito() {
        UUID autenticadoId = exigirAutenticado();
        // Estrito (Sprint 20): nenhum bypass. Exige MFA ATIVO no momento da operacao — nao basta o
        // token: um operador pode emitir step-up, desabilitar MFA dentro do TTL e reusar o token. A
        // verificacao do estado atual fecha essa janela antes de aceitar o token.
        Usuario usuario = usuarioRepository
                .findById(autenticadoId)
                .orElseThrow(() -> new AccessDeniedException("Usuario autenticado nao encontrado."));
        if (!usuario.isMfaHabilitado()) {
            throw new AccessDeniedException("Operacao sensivel exige MFA habilitado.");
        }
        exigirStepUpValido(autenticadoId);
    }

    private UUID exigirAutenticado() {
        UUID autenticadoId = principalId();
        if (autenticadoId == null) {
            throw new AccessDeniedException("Autenticacao requerida para operacao sensivel.");
        }
        return autenticadoId;
    }

    private void exigirStepUpValido(UUID autenticadoId) {
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
