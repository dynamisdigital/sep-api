package com.dynamis.sep_api.usuarios.application.usecase;

import com.dynamis.sep_api.identity.domain.vo.PasswordPolicy;
import com.dynamis.sep_api.usuarios.application.exception.UsernameJaExisteException;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioCreateDto;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioInternoCreateDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CriarUsuarioUseCase {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public CriarUsuarioUseCase(
            UsuarioRepository repository, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    /**
     * Cadastro publico (anonimo). Sempre cria {@link Role#CLIENTE}; criacao de ADMIN nao e
     * permitida por este caminho (follow-up 5F-FIX-01 da Sprint 5).
     */
    @Transactional
    public Usuario executar(UsuarioCreateDto dto) {
        return criar(dto.username(), dto.password(), Role.CLIENTE);
    }

    /**
     * Cadastro autenticado de usuario interno. Permite definir o {@link Role} explicitamente
     * (inclusive ADMIN). Deve ser invocado apenas por endpoint protegido com {@code
     * hasRole('ADMIN')}.
     */
    @Transactional
    public Usuario executarInterno(UsuarioInternoCreateDto dto) {
        return criar(dto.username(), dto.password(), dto.role());
    }

    private Usuario criar(String username, String password, Role role) {
        passwordPolicy.validar(password);

        if (repository.existsByUsername(username)) {
            throw new UsernameJaExisteException(username);
        }

        String hash = passwordEncoder.encode(password);
        Usuario novo = Usuario.criar(username, hash, role);
        return repository.save(novo);
    }
}
