package com.dynamis.sep_api.backoffice.web.controller;

import com.dynamis.sep_api.backoffice.application.usecase.ConsultarVisaoConsolidadaUseCase;
import com.dynamis.sep_api.backoffice.web.dto.DashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint do dashboard de visao consolidada (Sprint 14 Task 14.7). Sem cache.
 */
@RestController
@RequestMapping("/api/v1/backoffice/dashboard")
@Tag(name = "Backoffice — Dashboard")
@PreAuthorize("hasAnyRole('FINANCEIRO','BACKOFFICE','ADMIN')")
public class BackofficeDashboardController {

    private final ConsultarVisaoConsolidadaUseCase consultarVisao;

    public BackofficeDashboardController(ConsultarVisaoConsolidadaUseCase consultarVisao) {
        this.consultarVisao = consultarVisao;
    }

    @GetMapping
    @Operation(
            summary = "Dashboard consolidado da operacao de backoffice",
            description = "Roles aceitas: FINANCEIRO, BACKOFFICE ou ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Snapshot retornado."),
        @ApiResponse(responseCode = "401", description = "Sem autenticacao."),
        @ApiResponse(responseCode = "403", description = "Sem role FINANCEIRO/BACKOFFICE/ADMIN.")
    })
    public ResponseEntity<DashboardResponse> consultar() {
        DashboardResponse body = DashboardResponse.from(consultarVisao.consultar());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
