package com.dynamis.sep_api.credores.web.controller;

import com.dynamis.sep_api.credores.application.dto.AssociarOperacaoFinanciadaCommand;
import com.dynamis.sep_api.credores.application.usecase.AssociarOperacaoFinanciadaUseCase;
import com.dynamis.sep_api.credores.application.usecase.ConsultarCarteiraCredoraUseCase;
import com.dynamis.sep_api.credores.application.usecase.ConsultarOperacaoCarteiraUseCase;
import com.dynamis.sep_api.credores.web.dto.AssociarOperacaoRequest;
import com.dynamis.sep_api.credores.web.dto.OperacaoCarteiraResponse;
import com.dynamis.sep_api.credores.web.mapper.CarteiraCredoraWebMapper;
import com.dynamis.sep_api.identity.infrastructure.security.RequireStepUp;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/credores/carteira")
@Tag(name = "credores-carteira", description = "Carteira de operacoes financiadas da credora (Epic 10)")
public class EmpresaCredoraCarteiraController {

    private final ConsultarCarteiraCredoraUseCase consultarCarteiraUseCase;
    private final ConsultarOperacaoCarteiraUseCase consultarOperacaoUseCase;
    private final AssociarOperacaoFinanciadaUseCase associarUseCase;
    private final CarteiraCredoraWebMapper mapper;

    public EmpresaCredoraCarteiraController(
            ConsultarCarteiraCredoraUseCase consultarCarteiraUseCase,
            ConsultarOperacaoCarteiraUseCase consultarOperacaoUseCase,
            AssociarOperacaoFinanciadaUseCase associarUseCase,
            CarteiraCredoraWebMapper mapper) {
        this.consultarCarteiraUseCase = consultarCarteiraUseCase;
        this.consultarOperacaoUseCase = consultarOperacaoUseCase;
        this.associarUseCase = associarUseCase;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar a carteira de operacoes financiadas da credora autenticada")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Carteira retornada"),
        @ApiResponse(
                responseCode = "404",
                description = "Usuario nao possui credora",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<List<OperacaoCarteiraResponse>> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        List<OperacaoCarteiraResponse> resposta = consultarCarteiraUseCase.executar(principal.id()).stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Detalhe de uma operacao da carteira da credora autenticada")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operacao retornada"),
        @ApiResponse(
                responseCode = "404",
                description = "Credora ou operacao nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<OperacaoCarteiraResponse> consultar(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        return ResponseEntity.ok(mapper.toResponse(consultarOperacaoUseCase.executar(principal.id(), id)));
    }

    @PostMapping("/operacoes")
    @PreAuthorize("hasRole('ADMIN')")
    @RequireStepUp
    @Operation(summary = "Associar operacao financiada a carteira de uma credora (admin, assistida)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Operacao associada"),
        @ApiResponse(
                responseCode = "400",
                description = "contratoId diverge do contrato da oportunidade",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Nao e ADMIN ou step-up ausente",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Credora ou oportunidade nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Operacao ja existe para credora+contrato",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "422",
                description = "Credora nao elegivel ou contrato nao elegivel",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<OperacaoCarteiraResponse> associar(
            @Valid @RequestBody AssociarOperacaoRequest body, @AuthenticationPrincipal UsuarioAutenticado principal) {
        OperacaoCarteiraResponse resposta =
                mapper.toResponse(associarUseCase.executar(new AssociarOperacaoFinanciadaCommand(
                        body.empresaCredoraId(),
                        body.contratoId(),
                        body.oportunidadeId(),
                        body.justificativa(),
                        principal.id())));
        return ResponseEntity.created(URI.create("/api/v1/credores/carteira/" + resposta.id()))
                .body(resposta);
    }
}
