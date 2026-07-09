package com.dynamis.sep_api.credores.web.controller;

import com.dynamis.sep_api.credores.application.dto.RegistrarAporteCredoraCommand;
import com.dynamis.sep_api.credores.application.dto.RegistrarAporteCredoraResult;
import com.dynamis.sep_api.credores.application.usecase.RegistrarAporteCredoraUseCase;
import com.dynamis.sep_api.credores.web.dto.AporteCredoraResponse;
import com.dynamis.sep_api.credores.web.dto.RegistrarAporteRequest;
import com.dynamis.sep_api.identity.infrastructure.security.RequireStepUpEstrito;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Aporte assistido da credora em operacao financiada (Sprint 29 Task 29.4 — Epic 15). Operacao
 * financeira sensivel: o registro exige operador {@code FINANCEIRO}/{@code ADMIN}, {@code
 * Idempotency-Key} e step-up estrito ({@link RequireStepUpEstrito}, sem bypass de MFA). Provider
 * fake/local — nenhum dinheiro real e movido nesta fase (Celcoin/BaaS real na Fase 5).
 */
@RestController
@RequestMapping("/api/v1/credores/operacoes/{operacaoId}/aportes")
@Tag(name = "credores-aportes", description = "Aporte assistido da credora em operacao financiada (Epic 15)")
public class AporteCredoraController {

    private final RegistrarAporteCredoraUseCase registrarUseCase;

    public AporteCredoraController(RegistrarAporteCredoraUseCase registrarUseCase) {
        this.registrarUseCase = registrarUseCase;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @RequireStepUpEstrito
    @Operation(
            summary = "Registrar aporte assistido da credora",
            description = "Registra o aporte na operacao financiada da carteira e o movimenta no escrow"
                    + " (provider fake/local — sem dinheiro real; Celcoin/BaaS real na Fase 5). Exige"
                    + " Idempotency-Key e X-Step-Up-Token (step-up estrito, sem bypass de MFA)."
                    + " Reapresentacao com a mesma key e mesmo valor retorna o aporte existente (200);"
                    + " valor divergente retorna 409. Operacao precisa estar ativa na carteira e o contrato"
                    + " ASSINADO (409 caso contrario). Erros nao expoem UUIDs nem dados de escrow/provider.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Aporte registrado"),
        @ApiResponse(responseCode = "200", description = "Retorno idempotente do aporte existente"),
        @ApiResponse(
                responseCode = "400",
                description = "Idempotency-Key ausente ou corpo invalido",
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
                description = "Operacao nao encontrada para aporte",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Operacao nao elegivel (encerrada ou contrato nao assinado) ou"
                        + " Idempotency-Key conflitante",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<AporteCredoraResponse> registrar(
            @PathVariable UUID operacaoId,
            @Parameter(description = "Chave de idempotencia da operacao de aporte", required = true)
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @Valid @RequestBody RegistrarAporteRequest request,
            @AuthenticationPrincipal UsuarioAutenticado operador) {

        RegistrarAporteCredoraResult resultado = registrarUseCase.executar(
                new RegistrarAporteCredoraCommand(operacaoId, request.valor(), idempotencyKey, operador.id()));

        HttpStatus status = resultado.novo() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(AporteCredoraResponse.de(resultado.aporte()));
    }
}
