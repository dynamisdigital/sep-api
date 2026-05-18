package com.dynamis.sep_api.usuarios.application.usecase;

import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.shared.exception.AcessoNegadoException;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Altera o role de um usuario (Sprint 8 Task 8.4). Autorizacao por ADMIN + step-up token e
 * aplicada no endpoint (Spring Security {@code @PreAuthorize} + {@code @RequireStepUp}).
 *
 * <p>Regras de dominio:
 *
 * <ul>
 *   <li>ADMIN nao pode alterar a propria role (HTTP 403);
 *   <li>Operacao e idempotente: se a nova role e igual a atual, no-op (sem gravar audit);
 *   <li>Sempre gera evento {@link TipoEventoSeguranca#ROLE_ALTERADO} no audit log de seguranca
 *       (rastreabilidade regulatoria — operacao sensivel).
 * </ul>
 */
@Service
public class AlterarRoleUsuarioUseCase {

    public static final String CODIGO_AUTO_ALTERACAO = "USR-403-001";
    public static final String CODIGO_ROLE_INVALIDA = "USR-400-001";

    private final UsuarioRepository repository;
    private final AuditLogSegurancaService auditService;
    private final ObjectMapper objectMapper;

    public AlterarRoleUsuarioUseCase(
            UsuarioRepository repository, AuditLogSegurancaService auditService, ObjectMapper objectMapper) {
        this.repository = repository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
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

        auditService.gravar(
                TipoEventoSeguranca.ROLE_ALTERADO, atorAdminId, detalhes(usuarioAlvoId, anterior, novaRole));
        return salvo;
    }

    private String detalhes(UUID alvoId, Role anterior, Role nova) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("usuarioAlvoId", alvoId.toString());
        map.put("roleAnterior", anterior.name());
        map.put("roleNova", nova.name());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
