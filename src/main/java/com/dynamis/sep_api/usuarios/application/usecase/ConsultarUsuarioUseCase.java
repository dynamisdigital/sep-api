package com.dynamis.sep_api.usuarios.application.usecase;

import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ConsultarUsuarioUseCase {

    private final UsuarioRepository repository;

    public ConsultarUsuarioUseCase(UsuarioRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Usuario executar(UUID id, UsuarioAutenticado principal) {
        if (principal.role() != Role.ADMIN && !principal.id().equals(id)) {
            throw new AccessDeniedException("Acesso negado: cliente so pode consultar o proprio usuario");
        }
        return repository.findById(id).orElseThrow(() -> new UsuarioNaoEncontradoException(id));
    }
}
