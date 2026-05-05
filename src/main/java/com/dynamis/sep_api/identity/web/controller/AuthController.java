package com.dynamis.sep_api.identity.web.controller;

import com.dynamis.sep_api.identity.application.usecase.AutenticarUsuarioUseCase;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.identity.web.dto.LoginRequestDto;
import com.dynamis.sep_api.identity.web.dto.TokenResponseDto;
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;
import com.dynamis.sep_api.usuarios.web.mapper.UsuarioMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AutenticarUsuarioUseCase autenticarUsuarioUseCase;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper mapper;

    public AuthController(
            AutenticarUsuarioUseCase autenticarUsuarioUseCase,
            UsuarioRepository usuarioRepository,
            UsuarioMapper mapper) {
        this.autenticarUsuarioUseCase = autenticarUsuarioUseCase;
        this.usuarioRepository = usuarioRepository;
        this.mapper = mapper;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponseDto> login(@Valid @RequestBody LoginRequestDto dto) {
        return ResponseEntity.ok(autenticarUsuarioUseCase.executar(dto));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UsuarioResponseDto> me(@AuthenticationPrincipal UsuarioAutenticado principal) {
        Usuario usuario = usuarioRepository
                .findById(principal.id())
                .orElseThrow(() -> new UsuarioNaoEncontradoException(principal.id()));
        return ResponseEntity.ok(mapper.toResponse(usuario));
    }
}
