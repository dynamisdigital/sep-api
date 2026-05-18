package com.dynamis.sep_api.usuarios.application.usecase;

import com.dynamis.sep_api.shared.exception.AcessoNegadoException;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.event.RoleAlteradaEvent;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Altera o role de um usuario (Sprint 8 Task 8.4). Autorizacao por ADMIN + step-up token e
 * aplicada no endpoint (Spring Security {@code @PreAuthorize} + {@code @RequireStepUp}).
 *
 * <p>Regras de dominio:
 *
 * <ul>
 *   <li>ADMIN nao pode alterar a propria role (HTTP 403);
 *   <li>Operacao e idempotente: se a nova role e igual a atual, no-op (sem evento);
 *   <li>Sempre publica {@link RoleAlteradaEvent}; {@code UsuariosAuditListener} grava
 *       {@code ROLE_ALTERADO} via padrao AFTER_COMMIT + REQUIRES_NEW (fix code review Task 8.6 —
 *       consistencia com demais listeners de auditoria reforcada).
 * </ul>
 */
@Service
public class AlterarRoleUsuarioUseCase {

    public static final String CODIGO_AUTO_ALTERACAO = "USR-403-001";
    public static final String CODIGO_ROLE_INVALIDA = "USR-400-001";

    private final UsuarioRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public AlterarRoleUsuarioUseCase(UsuarioRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Usuario executar(UUID usuarioAlvoId, Role novaRole, UUID atorAdminId) {
        if (novaRole == null) {
            throw new ValidacaoException(CODIGO_ROLE_INVALIDA, "novaRole obrigatoria");
        }
        if (usuarioAlvoId.equals(atorAdminId)) {
            throw new AcessoNegadoException(CODIGO_AUTO_ALTERACAO, "ADMIN nao pode alterar a propria role");
        }

        Usuario alvo =
                repository.findById(usuarioAlvoId).orElseThrow(() -> new UsuarioNaoEncontradoException(usuarioAlvoId));
        Role anterior = alvo.getRole();
        if (anterior == novaRole) {
            return alvo;
        }

        alvo.alterarRole(novaRole);
        Usuario salvo = repository.save(alvo);

        eventPublisher.publishEvent(new RoleAlteradaEvent(atorAdminId, usuarioAlvoId, anterior, novaRole));
        return salvo;
    }
}
