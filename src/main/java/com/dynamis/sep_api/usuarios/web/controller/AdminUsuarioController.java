package com.dynamis.sep_api.usuarios.web.controller;

import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import com.dynamis.sep_api.usuarios.application.usecase.CriarUsuarioUseCase;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioInternoCreateDto;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;
import com.dynamis.sep_api.usuarios.web.mapper.UsuarioMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Cadastro autenticado de usuario interno (ADMIN/CLIENTE). Introduzido no follow-up 5F-FIX-01 da
 * Sprint 5 para isolar a criacao de ADMIN do cadastro publico em {@code POST /api/v1/usuarios}.
 */
@RestController
@RequestMapping("/api/v1/admin/usuarios")
@Tag(name = "usuarios-admin", description = "Cadastro autenticado de usuario interno (ADMIN apenas)")
public class AdminUsuarioController {

    private final CriarUsuarioUseCase criarUsuarioUseCase;
    private final UsuarioMapper mapper;

    public AdminUsuarioController(CriarUsuarioUseCase criarUsuarioUseCase, UsuarioMapper mapper) {
        this.criarUsuarioUseCase = criarUsuarioUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Criar usuario interno",
            description = "Cria usuario com role explicito (ADMIN ou CLIENTE). Restrito a ADMIN autenticado.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Usuario criado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = UsuarioResponseDto.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Requisicao invalida",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Acesso negado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Username ja cadastrado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<UsuarioResponseDto> criar(@Valid @RequestBody UsuarioInternoCreateDto dto) {
        Usuario salvo = criarUsuarioUseCase.executarInterno(dto);
        UsuarioResponseDto body = mapper.toResponse(salvo);
        URI location = URI.create("/api/v1/usuarios/" + salvo.getId());
        return ResponseEntity.created(location).body(body);
    }
}
