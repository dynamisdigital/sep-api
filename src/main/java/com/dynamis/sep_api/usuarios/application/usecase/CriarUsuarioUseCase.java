package com.dynamis.sep_api.usuarios.application.usecase;

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

    public CriarUsuarioUseCase(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Usuario executar(UsuarioCreateDto dto) {
        if (repository.existsByUsername(dto.username())) {
            throw new UsernameJaExisteException(dto.username());
        }

        String hash = passwordEncoder.encode(dto.password());
        Usuario novo = Usuario.criar(dto.username(), hash, dto.role());
        return repository.save(novo);
    }
}
