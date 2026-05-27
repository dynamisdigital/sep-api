package com.dynamis.sep_api.backoffice.web.controller;

import com.dynamis.sep_api.backoffice.application.usecase.ReprocessarChamadaProviderUseCase;
import com.dynamis.sep_api.backoffice.application.usecase.ReprocessarWebhookUseCase;
import com.dynamis.sep_api.backoffice.domain.vo.TipoChamadaProvider;
import com.dynamis.sep_api.backoffice.web.dto.ReprocessoRequest;
import com.dynamis.sep_api.backoffice.web.dto.ReprocessoResponse;
import com.dynamis.sep_api.identity.infrastructure.security.RequireStepUp;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoints de reprocesso manual (Sprint 14 Task 14.7). Step-up obrigatorio em ambos.
 *
 * <ul>
 *   <li>POST /api/v1/backoffice/reprocessos/webhook/{webhookEventId}
 *   <li>POST /api/v1/backoffice/reprocessos/provider/{tipoChamada}/{entidadeId}
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/backoffice/reprocessos")
@Tag(name = "Backoffice — Reprocessos")
@PreAuthorize("hasAnyRole('FINANCEIRO','BACKOFFICE','ADMIN')")
public class BackofficeReprocessoController {

    private final ReprocessarWebhookUseCase reprocessarWebhook;
    private final ReprocessarChamadaProviderUseCase reprocessarProvider;

    public BackofficeReprocessoController(
            ReprocessarWebhookUseCase reprocessarWebhook, ReprocessarChamadaProviderUseCase reprocessarProvider) {
        this.reprocessarWebhook = reprocessarWebhook;
        this.reprocessarProvider = reprocessarProvider;
    }

    @PostMapping("/webhook/{webhookEventId}")
    @RequireStepUp
    @Operation(
            summary = "Reprocessa webhook da Outbox",
            description = "Roles aceitas: FINANCEIRO, BACKOFFICE ou ADMIN. Exige step-up (header X-Step-Up-Token). "
                    + "Anti-abuso: 3 reprocessos por entidade em 24h.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Reprocesso registrado."),
        @ApiResponse(responseCode = "401", description = "Sem autenticacao."),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role FINANCEIRO/BACKOFFICE/ADMIN ou step-up ausente."),
        @ApiResponse(responseCode = "429", description = "Limite anti-abuso 3/24h excedido.")
    })
    public ResponseEntity<ReprocessoResponse> reprocessarWebhook(
            @PathVariable UUID webhookEventId,
            @RequestBody(required = false) @Valid ReprocessoRequest request,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        UUID itemId = request != null ? request.itemId() : null;
        var r = reprocessarWebhook.executar(webhookEventId, principal.id(), itemId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReprocessoResponse.from(r));
    }

    @PostMapping("/provider/{tipoChamada}/{entidadeId}")
    @RequireStepUp
    @Operation(
            summary = "Reprocessa chamada a provider externo",
            description = "Roles aceitas: FINANCEIRO, BACKOFFICE ou ADMIN. Exige step-up "
                    + "(header X-Step-Up-Token). Anti-abuso: 3 reprocessos por entidade em 24h. "
                    + "tipoChamada eh um de KYC/KYB/PLD/OPEN_FINANCE/ASSINATURA_DIGITAL.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Reprocesso registrado."),
        @ApiResponse(responseCode = "400", description = "tipoChamada nao suportado."),
        @ApiResponse(responseCode = "401", description = "Sem autenticacao."),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role FINANCEIRO/BACKOFFICE/ADMIN ou step-up ausente."),
        @ApiResponse(responseCode = "429", description = "Limite anti-abuso 3/24h excedido.")
    })
    public ResponseEntity<ReprocessoResponse> reprocessarProvider(
            @PathVariable TipoChamadaProvider tipoChamada,
            @PathVariable UUID entidadeId,
            @RequestBody(required = false) @Valid ReprocessoRequest request,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        UUID itemId = request != null ? request.itemId() : null;
        var r = reprocessarProvider.executar(tipoChamada, entidadeId, principal.id(), itemId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReprocessoResponse.from(r));
    }
}
