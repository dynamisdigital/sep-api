package com.dynamis.sep_api.usuarios.application.usecase;

import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
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

    public AlterarSenhaUseCase(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
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
        usuario.alterarSenha(passwordEncoder.encode(dto.novaSenha()));
        // dirty checking persiste no commit da transacao
    }
}
