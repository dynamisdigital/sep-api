package com.dynamis.sep_api.usuarios.web.controller;

import com.dynamis.sep_api.usuarios.application.usecase.CriarUsuarioUseCase;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioCreateDto;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;
import com.dynamis.sep_api.usuarios.web.mapper.UsuarioMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final CriarUsuarioUseCase criarUsuarioUseCase;
    private final UsuarioMapper mapper;

    public UsuarioController(CriarUsuarioUseCase criarUsuarioUseCase, UsuarioMapper mapper) {
        this.criarUsuarioUseCase = criarUsuarioUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<UsuarioResponseDto> criar(@Valid @RequestBody UsuarioCreateDto dto) {
        Usuario salvo = criarUsuarioUseCase.executar(dto);
        UsuarioResponseDto body = mapper.toResponse(salvo);
        URI location = URI.create("/api/v1/usuarios/" + salvo.getId());
        return ResponseEntity.created(location).body(body);
    }
}
