package com.dynamis.sep_api.usuarios.application.usecase;

import com.dynamis.sep_api.identity.domain.vo.PasswordPolicy;
import com.dynamis.sep_api.usuarios.application.exception.UsernameJaExisteException;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioCreateDto;
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

    @Transactional
    public Usuario executar(UsuarioCreateDto dto) {
        passwordPolicy.validar(dto.password());

        if (repository.existsByUsername(dto.username())) {
            throw new UsernameJaExisteException(dto.username());
        }

        String hash = passwordEncoder.encode(dto.password());
        Usuario novo = Usuario.criar(dto.username(), hash, dto.role());
        return repository.save(novo);
    }
}
