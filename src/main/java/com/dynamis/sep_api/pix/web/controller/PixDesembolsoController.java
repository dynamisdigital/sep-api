package com.dynamis.sep_api.pix.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.RequireStepUp;
import com.dynamis.sep_api.identity.infrastructure.security.RequireStepUpEstrito;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.pix.application.dto.ConsultarStatusDesembolsoPixCommand;
import com.dynamis.sep_api.pix.application.dto.SolicitarDesembolsoPixCommand;
import com.dynamis.sep_api.pix.application.dto.SolicitarDesembolsoPixResult;
import com.dynamis.sep_api.pix.application.dto.StatusDesembolsoPixResult;
import com.dynamis.sep_api.pix.application.usecase.ConsultarStatusDesembolsoPixUseCase;
import com.dynamis.sep_api.pix.application.usecase.SolicitarDesembolsoPixUseCase;
import com.dynamis.sep_api.pix.web.dto.DesembolsoResponse;
import com.dynamis.sep_api.pix.web.dto.SolicitarDesembolsoRequest;
import com.dynamis.sep_api.pix.web.dto.StatusDesembolsoResponse;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import com.dynamis.sep_api.shared.integration.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Desembolso Pix assistido (Sprint 20 Task 20.5). Operacao financeira sensivel: a solicitacao exige
 * operador {@code FINANCEIRO}/{@code ADMIN}, {@code Idempotency-Key} e step-up estrito
 * ({@link RequireStepUpEstrito} — sem bypass de MFA). Consulta/status sao read-only para
 * {@code FINANCEIRO}/{@code ADMIN}/{@code BACKOFFICE}.
 */
@RestController
@RequestMapping("/api/v1/pix/desembolsos")
@Tag(name = "pix-desembolsos", description = "Desembolso Pix assistido pelo financeiro")
public class PixDesembolsoController {

    private final SolicitarDesembolsoPixUseCase solicitarDesembolso;
    private final ConsultarStatusDesembolsoPixUseCase consultarStatus;

    public PixDesembolsoController(
            SolicitarDesembolsoPixUseCase solicitarDesembolso, ConsultarStatusDesembolsoPixUseCase consultarStatus) {
        this.solicitarDesembolso = solicitarDesembolso;
        this.consultarStatus = consultarStatus;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @RequireStepUpEstrito
    @Operation(
            summary = "Solicitar desembolso Pix",
            description = "Cria o desembolso para um contrato elegivel (ASSINADO + agenda ativa + escrow"
                    + " operacional). Exige Idempotency-Key e X-Step-Up-Token (step-up estrito, sem bypass de"
                    + " MFA). Reapresentacao com a mesma key/contrato/valor/chave retorna a transferencia"
                    + " existente (200); payload divergente retorna 409.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Desembolso criado"),
        @ApiResponse(responseCode = "200", description = "Retorno idempotente da transferencia existente"),
        @ApiResponse(
                responseCode = "400",
                description = "Idempotency-Key ausente ou corpo invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role autorizada ou sem step-up valido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Contrato nao encontrado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Desembolso duplicado por contrato ou Idempotency-Key conflitante",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "422",
                description = "Contrato inelegivel (nao assinado, sem agenda, escrow inoperante) ou valor divergente",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<DesembolsoResponse> solicitar(
            @Parameter(description = "Chave de idempotencia da operacao", required = true)
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @Valid @RequestBody SolicitarDesembolsoRequest request,
            @AuthenticationPrincipal UsuarioAutenticado operador) {

        SolicitarDesembolsoPixResult resultado = solicitarDesembolso.executar(new SolicitarDesembolsoPixCommand(
                request.contratoId(),
                request.valor(),
                request.chavePixDestino(),
                idempotencyKey,
                operador.id(),
                MDC.get(CorrelationIdFilter.MDC_KEY)));

        HttpStatus status = resultado.novo() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(DesembolsoResponse.de(resultado));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN','BACKOFFICE')")
    @Operation(
            summary = "Consultar status do desembolso (leitura local)",
            description = "Le o status persistido do desembolso, sem chamar o provider externo. Para reconciliar"
                    + " com o provider, use POST /status.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status atual"),
        @ApiResponse(
                responseCode = "404",
                description = "Transferencia nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<StatusDesembolsoResponse> consultar(@PathVariable UUID id) {
        StatusDesembolsoPixResult resultado = consultarStatus.executar(
                new ConsultarStatusDesembolsoPixCommand(id, MDC.get(CorrelationIdFilter.MDC_KEY), false));
        return ResponseEntity.ok(StatusDesembolsoResponse.de(resultado));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN','BACKOFFICE')")
    @RequireStepUp
    @Operation(
            summary = "Reconciliar status no provider",
            description = "Reconsulta o provider externo e sincroniza o status local idempotentemente (so avanca;"
                    + " terminal nao falha). Por chamar o provider e poder mudar o estado para CONCLUIDA/FALHOU,"
                    + " exige step-up (X-Step-Up-Token). Leitura resiliente: provider indisponivel devolve o"
                    + " status local com providerIndisponivel=true.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status reconciliado"),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role autorizada ou sem step-up valido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Transferencia nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<StatusDesembolsoResponse> reconsultar(@PathVariable UUID id) {
        StatusDesembolsoPixResult resultado = consultarStatus.executar(
                new ConsultarStatusDesembolsoPixCommand(id, MDC.get(CorrelationIdFilter.MDC_KEY), true));
        return ResponseEntity.ok(StatusDesembolsoResponse.de(resultado));
    }
}
