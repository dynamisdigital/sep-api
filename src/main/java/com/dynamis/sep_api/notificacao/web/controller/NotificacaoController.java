package com.dynamis.sep_api.notificacao.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.notificacao.application.usecase.ConsultarCentralNotificacoesUseCase;
import com.dynamis.sep_api.notificacao.application.usecase.MarcarNotificacaoLidaUseCase;
import com.dynamis.sep_api.notificacao.web.dto.NotificacaoResponse;
import com.dynamis.sep_api.notificacao.web.dto.NotificacoesNaoLidasResponse;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Central de notificacoes do usuario autenticado (Sprint 38 Task 38.4, ADR 0021 §9).
 *
 * <p>O dono e sempre o principal: nenhuma rota aceita usuario por parametro. A central mostra so o
 * canal {@code IN_APP}; e-mail enviado fica no historico, fora daqui.
 */
@RestController
@RequestMapping("/api/v1/notificacoes")
@PreAuthorize("isAuthenticated()")
@Tag(name = "notificacoes", description = "Central de notificacoes do usuario autenticado (Sprint 38).")
public class NotificacaoController {

    private final ConsultarCentralNotificacoesUseCase consultar;
    private final MarcarNotificacaoLidaUseCase marcarLida;

    public NotificacaoController(
            ConsultarCentralNotificacoesUseCase consultar, MarcarNotificacaoLidaUseCase marcarLida) {
        this.consultar = consultar;
        this.marcarLida = marcarLida;
    }

    @GetMapping
    @Operation(
            summary = "Lista as minhas notificacoes",
            description = "Notificacoes in-app do usuario autenticado, da mais recente para a mais antiga (desempate"
                    + " por id). Usuario sem notificacao recebe pagina vazia, nao erro. Nao inclui e-mails enviados.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pagina de notificacoes (pode ser vazia)"),
        @ApiResponse(
                responseCode = "400",
                description = "NTF-400-001: page menor que 0, ou size fora de 1 a 100",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Page<NotificacaoResponse>> listar(
            @Parameter(description = "Pagina, a partir de 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Itens por pagina, de 1 a 100") @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        return ResponseEntity.ok(consultar.listar(principal.id(), page, size).map(NotificacaoResponse::from));
    }

    @GetMapping("/nao-lidas/contagem")
    @Operation(
            summary = "Conta as minhas notificacoes nao lidas",
            description = "Mesmo recorte da listagem: notificacoes in-app do usuario autenticado ainda nao lidas.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contador de nao lidas (zero quando nao ha)"),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<NotificacoesNaoLidasResponse> contarNaoLidas(
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        return ResponseEntity.ok(new NotificacoesNaoLidasResponse(consultar.contarNaoLidas(principal.id())));
    }

    @PostMapping("/{id}/leitura")
    @Operation(
            summary = "Marca uma notificacao minha como lida",
            description = "Idempotente: marcar de novo devolve 200 com o instante da primeira leitura. Notificacao"
                    + " inexistente ou de outro usuario devolve o mesmo 404, sem identificador.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notificacao com lidaEm preenchido"),
        @ApiResponse(
                responseCode = "400",
                description = "id nao e UUID",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "NTF-404-001: notificacao inexistente ou de outro usuario",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<NotificacaoResponse> marcarComoLida(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        return ResponseEntity.ok(NotificacaoResponse.from(marcarLida.marcar(principal.id(), id)));
    }
}
