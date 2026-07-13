package com.dynamis.sep_api.credores.web.controller;

import com.dynamis.sep_api.credores.application.dto.DecidirMatchingCredoraOperacaoCommand;
import com.dynamis.sep_api.credores.application.usecase.ConsultarMatchingCredoraOperacaoUseCase;
import com.dynamis.sep_api.credores.application.usecase.DecidirMatchingCredoraOperacaoUseCase;
import com.dynamis.sep_api.credores.application.usecase.GerarSugestoesMatchingCredoraUseCase;
import com.dynamis.sep_api.credores.application.usecase.ListarSugestoesMatchingCredoraUseCase;
import com.dynamis.sep_api.credores.web.dto.DecidirMatchingRequest;
import com.dynamis.sep_api.credores.web.dto.MatchingSugestaoResponse;
import com.dynamis.sep_api.identity.infrastructure.security.RequireStepUpEstrito;
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

import java.util.List;
import java.util.UUID;

/**
 * Matching assistido credora-operacao (Sprint 30 Task 30.5 — Epic 15). O sistema sugere; a decisao
 * e sempre de operador {@code FINANCEIRO}/{@code ADMIN} — com step-up estrito ({@link
 * RequireStepUpEstrito}, sem bypass de MFA) somente na decisao. Nenhum matching e confirmado
 * automaticamente e a confirmacao nao dispara aporte, Pix, escrow ou chamada externa.
 */
@RestController
@RequestMapping("/api/v1/credores/matching")
@Tag(name = "credores-matching", description = "Matching assistido entre credora e operacao financiada (Epic 15)")
public class MatchingCredoraController {

    private final GerarSugestoesMatchingCredoraUseCase gerarUseCase;
    private final ListarSugestoesMatchingCredoraUseCase listarUseCase;
    private final ConsultarMatchingCredoraOperacaoUseCase consultarUseCase;
    private final DecidirMatchingCredoraOperacaoUseCase decidirUseCase;

    public MatchingCredoraController(
            GerarSugestoesMatchingCredoraUseCase gerarUseCase,
            ListarSugestoesMatchingCredoraUseCase listarUseCase,
            ConsultarMatchingCredoraOperacaoUseCase consultarUseCase,
            DecidirMatchingCredoraOperacaoUseCase decidirUseCase) {
        this.gerarUseCase = gerarUseCase;
        this.listarUseCase = listarUseCase;
        this.consultarUseCase = consultarUseCase;
        this.decidirUseCase = decidirUseCase;
    }

    @GetMapping("/sugestoes")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @Operation(
            summary = "Listar sugestoes de matching (refresh assistido)",
            description = "Gera sugestoes novas para pares elegiveis (refresh-on-read: persiste sugestao e"
                    + " auditoria por par novo; idempotente — par ja sugerido, confirmado ou rejeitado nao"
                    + " duplica) e lista as sugestoes SUGERIDA em ordem deterministica (maior valor"
                    + " elegivel primeiro). Sem step-up. Nao existe job automatico; nada e confirmado"
                    + " automaticamente. Auditoria CREDORA_MATCHING_SUGERIDA por sugestao nova.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sugestoes pendentes de decisao"),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role FINANCEIRO/ADMIN",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<List<MatchingSugestaoResponse>> listarSugestoes(
            @AuthenticationPrincipal UsuarioAutenticado operador) {
        gerarUseCase.executar(operador.id());
        List<MatchingSugestaoResponse> resposta = listarUseCase.executar().stream()
                .map(MatchingSugestaoResponse::de)
                .toList();
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/{sugestaoId}")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @Operation(
            summary = "Consultar sugestao de matching",
            description = "Consulta individual da sugestao (qualquer status) para financeiro/admin, sem"
                    + " step-up. Sugestao inexistente responde 404 neutro sem identificador.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sugestao encontrada"),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role FINANCEIRO/ADMIN",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Sugestao de matching nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<MatchingSugestaoResponse> consultar(@PathVariable UUID sugestaoId) {
        return ResponseEntity.ok(MatchingSugestaoResponse.de(consultarUseCase.executar(sugestaoId)));
    }

    @PostMapping("/{sugestaoId}/decisao")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @RequireStepUpEstrito
    @Operation(
            summary = "Decidir sugestao de matching (assistido)",
            description = "Confirma ou rejeita a sugestao SUGERIDA. Exige X-Step-Up-Token (step-up"
                    + " estrito, sem bypass de MFA). A confirmacao apenas registra a decisao do par"
                    + " credora-operacao: nenhum aporte e criado e nenhum escrow/provider e chamado —"
                    + " o aporte segue o fluxo assistido da Sprint 29. Decisao repetida ou sobre status"
                    + " terminal responde 409. Erros nao expoem UUIDs nem dados internos.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Decisao registrada"),
        @ApiResponse(
                responseCode = "400",
                description = "Acao invalida ou motivo acima do limite",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role autorizada ou sem step-up estrito valido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Sugestao de matching nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Sugestao ja decidida (status terminal)",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<MatchingSugestaoResponse> decidir(
            @PathVariable UUID sugestaoId,
            @Valid @RequestBody DecidirMatchingRequest request,
            @AuthenticationPrincipal UsuarioAutenticado operador) {
        MatchingSugestaoResponse resposta =
                MatchingSugestaoResponse.de(decidirUseCase.executar(new DecidirMatchingCredoraOperacaoCommand(
                        sugestaoId, request.acao(), request.motivo(), operador.id())));
        return ResponseEntity.ok(resposta);
    }
}
