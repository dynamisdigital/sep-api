package com.dynamis.sep_api.usuarios.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import com.dynamis.sep_api.usuarios.application.usecase.AlterarSenhaUseCase;
import com.dynamis.sep_api.usuarios.application.usecase.ConsultarUsuarioUseCase;
import com.dynamis.sep_api.usuarios.application.usecase.CriarUsuarioUseCase;
import com.dynamis.sep_api.usuarios.application.usecase.ListarUsuariosUseCase;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioCreateDto;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioSenhaUpdateDto;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "usuarios", description = "Cadastro e gestao de usuarios")
public class UsuarioController {

    private final CriarUsuarioUseCase criarUsuarioUseCase;
    private final ConsultarUsuarioUseCase consultarUsuarioUseCase;
    private final ListarUsuariosUseCase listarUsuariosUseCase;
    private final AlterarSenhaUseCase alterarSenhaUseCase;
    private final UsuarioMapper mapper;

    public UsuarioController(
            CriarUsuarioUseCase criarUsuarioUseCase,
            ConsultarUsuarioUseCase consultarUsuarioUseCase,
            ListarUsuariosUseCase listarUsuariosUseCase,
            AlterarSenhaUseCase alterarSenhaUseCase,
            UsuarioMapper mapper) {
        this.criarUsuarioUseCase = criarUsuarioUseCase;
        this.consultarUsuarioUseCase = consultarUsuarioUseCase;
        this.listarUsuariosUseCase = listarUsuariosUseCase;
        this.alterarSenhaUseCase = alterarSenhaUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Criar usuario", description = "Cria usuario sem autenticacao (cadastro publico).")
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
                responseCode = "409",
                description = "Username ja cadastrado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<UsuarioResponseDto> criar(@Valid @RequestBody UsuarioCreateDto dto) {
        Usuario salvo = criarUsuarioUseCase.executar(dto);
        UsuarioResponseDto body = mapper.toResponse(salvo);
        URI location = URI.create("/api/v1/usuarios/" + salvo.getId());
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Buscar usuario por id", description = "Admin acessa qualquer; cliente apenas o proprio.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Usuario encontrado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = UsuarioResponseDto.class))),
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
                responseCode = "404",
                description = "Usuario nao encontrado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<UsuarioResponseDto> consultar(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        Usuario usuario = consultarUsuarioUseCase.executar(id, principal);
        return ResponseEntity.ok(mapper.toResponse(usuario));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar usuarios", description = "Apenas ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de usuarios"),
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
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<List<UsuarioResponseDto>> listar() {
        List<UsuarioResponseDto> resp = listarUsuariosUseCase.executar().stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(resp);
    }

    @PatchMapping("/{id}/senha")
    @PreAuthorize("isAuthenticated()")
    @com.dynamis.sep_api.identity.infrastructure.security.RequireStepUp
    @Operation(
            summary = "Alterar senha",
            description =
                    "Apenas o proprio usuario altera a propria senha. Sprint 5: exige step-up token (X-Step-Up-Token)"
                            + " quando MFA habilitado.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Senha alterada"),
        @ApiResponse(
                responseCode = "400",
                description = "Senha atual incorreta ou requisicao invalida",
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
                responseCode = "404",
                description = "Usuario nao encontrado",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> alterarSenha(
            @PathVariable UUID id,
            @Valid @RequestBody UsuarioSenhaUpdateDto dto,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        alterarSenhaUseCase.executar(id, dto, principal);
        return ResponseEntity.noContent().build();
    }
}
