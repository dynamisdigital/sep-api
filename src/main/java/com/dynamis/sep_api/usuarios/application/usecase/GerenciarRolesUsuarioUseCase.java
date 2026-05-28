package com.dynamis.sep_api.usuarios.application.usecase;

import com.dynamis.sep_api.shared.exception.AcessoNegadoException;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.event.RolesUsuarioAlteradasEvent;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Gestao do conjunto cumulativo de roles de um usuario (Sprint 18 Task 18.5). Autorizacao
 * (ADMIN + step-up) aplicada no endpoint. Regra de seguranca: um admin nao pode alterar as
 * proprias roles (evita auto-escalacao/auto-rebaixamento acidental). Publica {@link
 * RolesUsuarioAlteradasEvent} quando o conjunto efetivamente muda.
 */
@Service
public class GerenciarRolesUsuarioUseCase {

    public static final String CODIGO_AUTO_ALTERACAO = "USR-403-002";
    public static final String CODIGO_ULTIMA_ROLE = "USR-400-002";

    private final UsuarioRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public GerenciarRolesUsuarioUseCase(UsuarioRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Set<Role> consultar(UUID usuarioId) {
        return carregar(usuarioId).getRoles();
    }

    @Transactional
    public Usuario substituir(UUID alvoId, Set<Role> novasRoles, UUID atorId) {
        if (novasRoles == null || novasRoles.isEmpty()) {
            throw new IllegalArgumentException("conjunto de roles obrigatorio");
        }
        bloquearAutoAlteracao(alvoId, atorId);
        Usuario alvo = carregar(alvoId);
        Set<Role> anteriores = EnumSet.copyOf(alvo.getRoles());
        if (anteriores.equals(novasRoles)) {
            return alvo;
        }
        alvo.substituirRoles(novasRoles);
        return salvarEPublicar(alvo, anteriores, atorId);
    }

    @Transactional
    public Usuario adicionar(UUID alvoId, Role role, UUID atorId) {
        bloquearAutoAlteracao(alvoId, atorId);
        Usuario alvo = carregar(alvoId);
        if (alvo.possuiRole(role)) {
            return alvo;
        }
        Set<Role> anteriores = EnumSet.copyOf(alvo.getRoles());
        alvo.adicionarRole(role);
        return salvarEPublicar(alvo, anteriores, atorId);
    }

    @Transactional
    public Usuario remover(UUID alvoId, Role role, UUID atorId) {
        bloquearAutoAlteracao(alvoId, atorId);
        Usuario alvo = carregar(alvoId);
        if (!alvo.possuiRole(role)) {
            return alvo;
        }
        if (alvo.getRoles().size() == 1) {
            throw new ValidacaoException(CODIGO_ULTIMA_ROLE, "usuario deve manter ao menos uma role");
        }
        Set<Role> anteriores = EnumSet.copyOf(alvo.getRoles());
        alvo.removerRole(role);
        return salvarEPublicar(alvo, anteriores, atorId);
    }

    private Usuario salvarEPublicar(Usuario alvo, Set<Role> anteriores, UUID atorId) {
        Usuario salvo = repository.save(alvo);
        eventPublisher.publishEvent(
                new RolesUsuarioAlteradasEvent(atorId, salvo.getId(), anteriores, EnumSet.copyOf(salvo.getRoles())));
        return salvo;
    }

    private Usuario carregar(UUID id) {
        return repository.findById(id).orElseThrow(() -> new UsuarioNaoEncontradoException(id));
    }

    private void bloquearAutoAlteracao(UUID alvoId, UUID atorId) {
        if (alvoId.equals(atorId)) {
            throw new AcessoNegadoException(CODIGO_AUTO_ALTERACAO, "ADMIN nao pode alterar as proprias roles");
        }
    }
}
