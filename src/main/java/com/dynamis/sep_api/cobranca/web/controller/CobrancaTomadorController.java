package com.dynamis.sep_api.cobranca.web.controller;

import com.dynamis.sep_api.cobranca.application.usecase.ConsultarRecebimentosParcelaUseCase;
import com.dynamis.sep_api.cobranca.application.usecase.ConsultarRenegociacaoAtivaTomadorUseCase;
import com.dynamis.sep_api.cobranca.web.dto.RecebimentoTomadorResponse;
import com.dynamis.sep_api.cobranca.web.dto.RenegociacaoTomadorResponse;
import com.dynamis.sep_api.cobranca.web.mapper.CobrancaWebMapper;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Consultas owner-scoped do tomador no modulo cobranca (Sprint 23 — desbloqueio B1 da M-Sprint 9).
 *
 * <p>Separado do {@link CobrancaController} operacional de proposito: o tomador (ROLE_CLIENTE)
 * enxerga apenas um recorte publico minimo dos proprios recebimentos, sem os campos operacionais
 * expostos aos perfis FINANCEIRO/ADMIN. A ownership e validada no use case.
 */
@RestController
@RequestMapping("/api/v1/cobranca")
@Tag(name = "cobranca-tomador", description = "Consultas owner-scoped do tomador (Sprint 23).")
public class CobrancaTomadorController {

    private final ConsultarRecebimentosParcelaUseCase consultarRecebimentosParcelaUseCase;
    private final ConsultarRenegociacaoAtivaTomadorUseCase consultarRenegociacaoAtivaTomadorUseCase;
    private final CobrancaWebMapper mapper;

    public CobrancaTomadorController(
            ConsultarRecebimentosParcelaUseCase consultarRecebimentosParcelaUseCase,
            ConsultarRenegociacaoAtivaTomadorUseCase consultarRenegociacaoAtivaTomadorUseCase,
            CobrancaWebMapper mapper) {
        this.consultarRecebimentosParcelaUseCase = consultarRecebimentosParcelaUseCase;
        this.consultarRenegociacaoAtivaTomadorUseCase = consultarRenegociacaoAtivaTomadorUseCase;
        this.mapper = mapper;
    }

    @GetMapping("/parcelas/{parcelaId}/recebimentos")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(
            summary = "Historico de recebimentos de uma parcela propria",
            description = "Tomador lista os recebimentos de uma parcela de contrato proprio, ordenados por"
                    + " data decrescente. Parcela inexistente ou de outro tomador retorna 403 uniforme, sem"
                    + " identificador, para nao permitir enumeracao. Exclusivo de ROLE_CLIENTE.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de recebimentos (pode estar vazia)"),
        @ApiResponse(
                responseCode = "400",
                description = "parcelaId invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role CLIENTE, parcela inexistente ou de outro tomador",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<List<RecebimentoTomadorResponse>> listarRecebimentosDaParcela(
            @PathVariable UUID parcelaId, @AuthenticationPrincipal UsuarioAutenticado principal) {
        var recebimentos = consultarRecebimentosParcelaUseCase.executar(parcelaId, principal.id());
        return ResponseEntity.ok(mapper.toRecebimentoTomadorListResponse(recebimentos));
    }

    @GetMapping("/parcelas/{parcelaId}/renegociacao-ativa")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(
            summary = "Termos da renegociacao ativa de uma parcela propria",
            description = "Tomador consulta os termos financeiros de uma renegociacao ativa (PROPOSTA nao"
                    + " expirada) de uma parcela de contrato proprio, antes de aceitar ou recusar. Read-only,"
                    + " sem step-up. Parcela inexistente ou de outro tomador retorna 403 uniforme, sem"
                    + " identificador, para nao permitir enumeracao. Parcela propria sem proposta ativa retorna"
                    + " 404. Exclusivo de ROLE_CLIENTE.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Termos da renegociacao ativa"),
        @ApiResponse(
                responseCode = "400",
                description = "parcelaId invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role CLIENTE, parcela inexistente ou de outro tomador",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Parcela propria sem renegociacao ativa",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<RenegociacaoTomadorResponse> consultarRenegociacaoAtiva(
            @PathVariable UUID parcelaId, @AuthenticationPrincipal UsuarioAutenticado principal) {
        var renegociacao = consultarRenegociacaoAtivaTomadorUseCase.executar(parcelaId, principal.id());
        return ResponseEntity.ok(mapper.toRenegociacaoTomadorResponse(renegociacao));
    }
}
