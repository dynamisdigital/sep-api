package com.dynamis.sep_api.credores.web.controller;

import com.dynamis.sep_api.credores.application.dto.CadastrarEmpresaCredoraCommand;
import com.dynamis.sep_api.credores.application.dto.EmpresaCredoraView;
import com.dynamis.sep_api.credores.application.usecase.CadastrarEmpresaCredoraUseCase;
import com.dynamis.sep_api.credores.application.usecase.ConsultarEmpresaCredoraPorIdUseCase;
import com.dynamis.sep_api.credores.application.usecase.ConsultarEmpresaCredoraPropriaUseCase;
import com.dynamis.sep_api.credores.web.dto.CadastrarEmpresaCredoraRequest;
import com.dynamis.sep_api.credores.web.dto.ElegibilidadeResponse;
import com.dynamis.sep_api.credores.web.dto.EmpresaCredoraResponse;
import com.dynamis.sep_api.credores.web.mapper.EmpresaCredoraWebMapper;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/credores")
@Tag(name = "credores", description = "Jornada da empresa credora (Epic 10, Resolucao CMN 4.656/2018)")
public class EmpresaCredoraController {

    private final CadastrarEmpresaCredoraUseCase cadastrarUseCase;
    private final ConsultarEmpresaCredoraPropriaUseCase consultarPropriaUseCase;
    private final ConsultarEmpresaCredoraPorIdUseCase consultarPorIdUseCase;
    private final EmpresaCredoraWebMapper mapper;

    public EmpresaCredoraController(
            CadastrarEmpresaCredoraUseCase cadastrarUseCase,
            ConsultarEmpresaCredoraPropriaUseCase consultarPropriaUseCase,
            ConsultarEmpresaCredoraPorIdUseCase consultarPorIdUseCase,
            EmpresaCredoraWebMapper mapper) {
        this.cadastrarUseCase = cadastrarUseCase;
        this.consultarPropriaUseCase = consultarPropriaUseCase;
        this.consultarPorIdUseCase = consultarPorIdUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cadastrar empresa credora a partir de onboarding PJ aprovado")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Credora cadastrada"),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Onboarding pertence a outro usuario",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Onboarding nao encontrado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Usuario, onboarding ou CNPJ ja vinculado a uma credora",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "422",
                description = "Onboarding nao e PJ ou ainda sem KYB",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<EmpresaCredoraResponse> cadastrar(
            @Valid @RequestBody CadastrarEmpresaCredoraRequest body,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        EmpresaCredoraView view = cadastrarUseCase.executar(new CadastrarEmpresaCredoraCommand(
                principal.id(), body.onboardingId(), body.tipoCredora(), body.capacidadeAporte()));
        return ResponseEntity.created(URI.create("/api/v1/credores/" + view.id()))
                .body(mapper.toResponse(view));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar a empresa credora do usuario autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Credora retornada"),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Usuario nao possui credora",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<EmpresaCredoraResponse> consultarPropria(
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        return ResponseEntity.ok(mapper.toResponse(consultarPropriaUseCase.executar(principal.id())));
    }

    @GetMapping("/me/elegibilidade")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar o status de elegibilidade da credora do usuario autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status de elegibilidade retornado"),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Usuario nao possui credora",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ElegibilidadeResponse> consultarElegibilidade(
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        return ResponseEntity.ok(mapper.toElegibilidadeResponse(consultarPropriaUseCase.executar(principal.id())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Consulta administrativa de qualquer empresa credora pelo id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Credora retornada"),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Nao e ADMIN",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Credora nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<EmpresaCredoraResponse> consultarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toResponse(consultarPorIdUseCase.executar(id)));
    }
}
