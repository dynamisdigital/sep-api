package com.dynamis.sep_api.governanca.web.controller;

import com.dynamis.sep_api.governanca.application.dto.AlterarParametroCommand;
import com.dynamis.sep_api.governanca.application.dto.ParametroComHistoricoView;
import com.dynamis.sep_api.governanca.application.dto.ParametroOperacionalView;
import com.dynamis.sep_api.governanca.application.usecase.AlterarParametroOperacionalUseCase;
import com.dynamis.sep_api.governanca.application.usecase.ConsultarParametroOperacionalUseCase;
import com.dynamis.sep_api.governanca.application.usecase.ListarParametrosOperacionaisUseCase;
import com.dynamis.sep_api.governanca.web.dto.AlterarParametroRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/governanca/parametros")
@Tag(name = "governanca", description = "Parametros operacionais governados (Epic 11)")
public class GovernancaParametroController {

    private final ListarParametrosOperacionaisUseCase listarUseCase;
    private final ConsultarParametroOperacionalUseCase consultarUseCase;
    private final AlterarParametroOperacionalUseCase alterarUseCase;

    public GovernancaParametroController(
            ListarParametrosOperacionaisUseCase listarUseCase,
            ConsultarParametroOperacionalUseCase consultarUseCase,
            AlterarParametroOperacionalUseCase alterarUseCase) {
        this.listarUseCase = listarUseCase;
        this.consultarUseCase = consultarUseCase;
        this.alterarUseCase = alterarUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar parametros operacionais governados (admin)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Parametros retornados"),
        @ApiResponse(
                responseCode = "403",
                description = "Nao e ADMIN",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<List<ParametroOperacionalView>> listar() {
        return ResponseEntity.ok(listarUseCase.executar());
    }

    @GetMapping("/{chave}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Detalhe de um parametro operacional com historico (admin)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Parametro + historico"),
        @ApiResponse(
                responseCode = "403",
                description = "Nao e ADMIN",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Parametro nao encontrado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ParametroComHistoricoView> consultar(@PathVariable String chave) {
        return ResponseEntity.ok(consultarUseCase.executar(chave));
    }

    @PatchMapping("/{chave}")
    @PreAuthorize("hasRole('ADMIN')")
    @RequireStepUp
    @Operation(summary = "Alterar valor de um parametro operacional (admin + step-up)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Parametro alterado"),
        @ApiResponse(
                responseCode = "400",
                description = "Valor invalido para o tipo do parametro",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Nao e ADMIN ou step-up ausente",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Parametro nao encontrado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ParametroOperacionalView> alterar(
            @PathVariable String chave,
            @Valid @RequestBody AlterarParametroRequest body,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        return ResponseEntity.ok(alterarUseCase.executar(
                new AlterarParametroCommand(chave, body.novoValor(), body.justificativa(), principal.id())));
    }
}
