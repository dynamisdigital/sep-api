package com.dynamis.sep_api.pix.web.controller;

import com.dynamis.sep_api.pix.application.dto.GerarReferenciaRecebimentoPixCommand;
import com.dynamis.sep_api.pix.application.dto.GerarReferenciaRecebimentoPixResult;
import com.dynamis.sep_api.pix.application.usecase.ConsultarRecebimentoPixUseCase;
import com.dynamis.sep_api.pix.application.usecase.ConsultarReferenciaRecebimentoPixUseCase;
import com.dynamis.sep_api.pix.application.usecase.GerarReferenciaRecebimentoPixUseCase;
import com.dynamis.sep_api.pix.web.dto.GerarReferenciaRecebimentoRequest;
import com.dynamis.sep_api.pix.web.dto.RecebimentoPixResponse;
import com.dynamis.sep_api.pix.web.dto.ReferenciaRecebimentoResponse;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import com.dynamis.sep_api.shared.integration.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Recebimento Pix de parcelas (Sprint 21 Task 21.6). Gerar a referencia apenas cria a cobranca Pix
 * para pagamento da propria parcela — operacao nao sensivel, sem step-up. Nesta sprint a geracao eh
 * restrita a {@code FINANCEIRO}/{@code ADMIN} (self-service do tomador fica para o front das jornadas).
 * As consultas sao read-only para papeis internos, incluindo {@code BACKOFFICE} para tratar
 * divergencias.
 */
@RestController
@RequestMapping("/api/v1/pix/recebimentos")
@Tag(name = "pix-recebimentos", description = "Referencias e recebimentos Pix de parcelas")
public class PixRecebimentoController {

    private final GerarReferenciaRecebimentoPixUseCase gerarReferencia;
    private final ConsultarReferenciaRecebimentoPixUseCase consultarReferencia;
    private final ConsultarRecebimentoPixUseCase consultarRecebimento;

    public PixRecebimentoController(
            GerarReferenciaRecebimentoPixUseCase gerarReferencia,
            ConsultarReferenciaRecebimentoPixUseCase consultarReferencia,
            ConsultarRecebimentoPixUseCase consultarRecebimento) {
        this.gerarReferencia = gerarReferencia;
        this.consultarReferencia = consultarReferencia;
        this.consultarRecebimento = consultarRecebimento;
    }

    @PostMapping("/referencias")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @Operation(
            summary = "Gerar referencia Pix de recebimento",
            description = "Gera (ou reaproveita) a referencia Pix com txid deterministico para uma parcela elegivel."
                    + " Reapresentacao para parcela com referencia ATIVA retorna a existente (200).")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Referencia criada"),
        @ApiResponse(responseCode = "200", description = "Retorno idempotente da referencia ATIVA existente"),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role autorizada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Parcela nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Geracao concorrente de referencia para a parcela",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "422",
                description = "Parcela nao recebivel ou sem valor em aberto",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ReferenciaRecebimentoResponse> gerar(
            @Valid @RequestBody GerarReferenciaRecebimentoRequest request) {
        GerarReferenciaRecebimentoPixResult resultado = gerarReferencia.executar(
                new GerarReferenciaRecebimentoPixCommand(request.parcelaId(), MDC.get(CorrelationIdFilter.MDC_KEY)));
        HttpStatus status = resultado.novo() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ReferenciaRecebimentoResponse.de(resultado));
    }

    @GetMapping("/referencias/{id}")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN','BACKOFFICE')")
    @Operation(summary = "Consultar referencia Pix de recebimento (leitura local)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Referencia encontrada"),
        @ApiResponse(
                responseCode = "404",
                description = "Referencia nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ReferenciaRecebimentoResponse> consultarReferencia(@PathVariable UUID id) {
        return ResponseEntity.ok(ReferenciaRecebimentoResponse.de(consultarReferencia.executar(id)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN','BACKOFFICE')")
    @Operation(summary = "Consultar recebimento Pix (leitura local, operacao assistida)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recebimento encontrado"),
        @ApiResponse(
                responseCode = "404",
                description = "Recebimento nao encontrado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<RecebimentoPixResponse> consultarRecebimento(@PathVariable UUID id) {
        return ResponseEntity.ok(RecebimentoPixResponse.de(consultarRecebimento.executar(id)));
    }
}
