package com.dynamis.sep_api.usuarios.application.usecase;

import com.dynamis.sep_api.identity.domain.vo.PasswordPolicy;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaService;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.usuarios.application.exception.SenhaAtualIncorretaException;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioSenhaUpdateDto;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AlterarSenhaUseCase {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditLogSegurancaService auditService;

    public AlterarSenhaUseCase(
            UsuarioRepository repository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            AuditLogSegurancaService auditService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
    }

    @Transactional
    public void executar(UUID id, UsuarioSenhaUpdateDto dto, UsuarioAutenticado principal) {
        if (!principal.id().equals(id)) {
            throw new AccessDeniedException("Acesso negado: somente o proprio usuario pode alterar sua senha");
        }
        Usuario usuario = repository.findById(id).orElseThrow(() -> new UsuarioNaoEncontradoException(id));
        if (!passwordEncoder.matches(dto.passwordAtual(), usuario.getPassword())) {
            throw new SenhaAtualIncorretaException();
        }
        passwordPolicy.validar(dto.novaSenha());
        usuario.alterarSenha(passwordEncoder.encode(dto.novaSenha()));
        // dirty checking persiste no commit da transacao; alterarSenha tambem zera precisaRedefinirSenha
        auditService.gravar(TipoEventoSeguranca.PASSWORD_CHANGED, id);
    }
}
