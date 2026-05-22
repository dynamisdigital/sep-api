package com.dynamis.sep_api.cobranca.web.controller;

import com.dynamis.sep_api.cobranca.application.dto.ParcelaAtualizadaResult;
import com.dynamis.sep_api.cobranca.application.dto.RegistrarRecebimentoCommand;
import com.dynamis.sep_api.cobranca.application.dto.RegistrarRecebimentoResult;
import com.dynamis.sep_api.cobranca.application.port.out.ContratoCobrancaQueryPort;
import com.dynamis.sep_api.cobranca.application.usecase.CalcularValorAtualizadoParcelaUseCase;
import com.dynamis.sep_api.cobranca.application.usecase.ConsultarAgendaPorContratoUseCase;
import com.dynamis.sep_api.cobranca.application.usecase.ConsultarRecebimentosUseCase;
import com.dynamis.sep_api.cobranca.application.usecase.RegistrarRecebimentoUseCase;
import com.dynamis.sep_api.cobranca.domain.exception.CobrancaOwnershipException;
import com.dynamis.sep_api.cobranca.domain.model.AgendaPagamento;
import com.dynamis.sep_api.cobranca.web.dto.AgendaPagamentoResponse;
import com.dynamis.sep_api.cobranca.web.dto.RecebimentoResponse;
import com.dynamis.sep_api.cobranca.web.dto.RegistrarRecebimentoRequest;
import com.dynamis.sep_api.cobranca.web.dto.ValorAtualizadoParcelaResponse;
import com.dynamis.sep_api.cobranca.web.mapper.CobrancaWebMapper;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import com.dynamis.sep_api.usuarios.domain.model.Role;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Endpoints REST do modulo {@code cobranca} (Sprint 12 Task 12.6).
 *
 * <ul>
 *   <li>{@code GET /api/v1/cobranca/contratos/{contratoId}/agenda} — owner ou FINANCEIRO/ADMIN
 *   <li>{@code GET /api/v1/cobranca/parcelas/{id}} — owner ou FINANCEIRO/ADMIN; retorna composicao
 *       atualizada com mora+multa pro-rata calculada contra {@code Clock} injetado
 *   <li>{@code POST /api/v1/cobranca/parcelas/{id}/recebimentos} — FINANCEIRO + Idempotency-Key
 *       obrigatorio
 *   <li>{@code GET /api/v1/cobranca/recebimentos} — FINANCEIRO; listagem ordenada
 *       por dataRecebimento DESC
 * </ul>
 *
 * <p>Controller depende apenas dos use cases e da {@link ContratoCobrancaQueryPort} (ADR 0007):
 * cobranca nao conhece a persistencia do modulo {@code contratos}.
 */
@RestController
@RequestMapping("/api/v1/cobranca")
@Tag(name = "cobranca", description = "Agenda de pagamento, parcelas, recebimentos e valores atualizados (Sprint 12).")
public class CobrancaController {

    /** Pattern conservador pra Idempotency-Key — recusa espacos, unicode, controle. */
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    private final ConsultarAgendaPorContratoUseCase consultarAgendaUseCase;
    private final CalcularValorAtualizadoParcelaUseCase calcularValorAtualizadoUseCase;
    private final RegistrarRecebimentoUseCase registrarRecebimentoUseCase;
    private final ConsultarRecebimentosUseCase consultarRecebimentosUseCase;
    private final ContratoCobrancaQueryPort contratoQueryPort;
    private final CobrancaWebMapper mapper;

    public CobrancaController(
            ConsultarAgendaPorContratoUseCase consultarAgendaUseCase,
            CalcularValorAtualizadoParcelaUseCase calcularValorAtualizadoUseCase,
            RegistrarRecebimentoUseCase registrarRecebimentoUseCase,
            ConsultarRecebimentosUseCase consultarRecebimentosUseCase,
            ContratoCobrancaQueryPort contratoQueryPort,
            CobrancaWebMapper mapper) {
        this.consultarAgendaUseCase = consultarAgendaUseCase;
        this.calcularValorAtualizadoUseCase = calcularValorAtualizadoUseCase;
        this.registrarRecebimentoUseCase = registrarRecebimentoUseCase;
        this.consultarRecebimentosUseCase = consultarRecebimentosUseCase;
        this.contratoQueryPort = contratoQueryPort;
        this.mapper = mapper;
    }

    @GetMapping("/contratos/{contratoId}/agenda")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Consultar agenda de pagamento por contrato",
            description = "Cliente acessa apenas a agenda do proprio contrato; FINANCEIRO/ADMIN acessa qualquer.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Agenda retornada",
                content = @Content(schema = @Schema(implementation = AgendaPagamentoResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "contratoId invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Contrato de outro tomador",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Agenda nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<AgendaPagamentoResponse> consultarAgenda(
            @PathVariable UUID contratoId, @AuthenticationPrincipal UsuarioAutenticado principal) {
        garantirOwnershipOuOperador(contratoId, principal);
        AgendaPagamento agenda = consultarAgendaUseCase.executar(contratoId);
        return ResponseEntity.ok(mapper.toAgendaResponse(agenda));
    }

    @GetMapping("/parcelas/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Consultar parcela com valor atualizado",
            description = "Retorna composicao original + mora/multa pro-rata calculada contra 'agora'."
                    + " Owner do contrato ou FINANCEIRO/ADMIN.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Parcela atualizada",
                content = @Content(schema = @Schema(implementation = ValorAtualizadoParcelaResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "id invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Parcela de contrato de outro tomador",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Parcela nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ValorAtualizadoParcelaResponse> consultarParcela(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        // Sprint 12 Task 12.6 fix code review manual: resolver contratoId ANTES de calcular
        // pra evitar enumeracao 404 vs 403 por CLIENTE. Cliente sem acesso recebe 403 mesmo
        // quando a parcela nao existe.
        if (!operadorInterno(principal)) {
            UUID contratoId = calcularValorAtualizadoUseCase
                    .resolverContratoId(id)
                    .orElseThrow(() -> new CobrancaOwnershipException(id));
            garantirOwnershipOuOperador(contratoId, principal);
        }
        ParcelaAtualizadaResult atualizado = calcularValorAtualizadoUseCase.executar(id);
        return ResponseEntity.ok(mapper.toValorAtualizadoResponse(atualizado));
    }

    @PostMapping("/parcelas/{id}/recebimentos")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @Operation(
            summary = "Registrar recebimento manual em parcela",
            description = "Idempotente por header Idempotency-Key. Reapresentacao da mesma key retorna o"
                    + " recebimento original sem duplicar movimentacao escrow. Header ausente retorna 400.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Recebimento registrado",
                content = @Content(schema = @Schema(implementation = RecebimentoResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Header Idempotency-Key ausente ou payload invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
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
                description = "Parcela nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Idempotency-Key reapresentada com payload divergente ou parcela em estado nao-recebivel",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<RecebimentoResponse> registrarRecebimento(
            @PathVariable UUID id,
            @Valid @RequestBody RegistrarRecebimentoRequest request,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        validarIdempotencyKey(idempotencyKey);
        RegistrarRecebimentoResult result = registrarRecebimentoUseCase.executar(new RegistrarRecebimentoCommand(
                id,
                request.valorRecebido(),
                request.dataRecebimento(),
                request.meioPagamento(),
                request.identificadorExterno(),
                idempotencyKey,
                request.observacao(),
                principal.id()));
        return ResponseEntity.ok(mapper.toRecebimentoResponse(result));
    }

    @GetMapping("/recebimentos")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @Operation(
            summary = "Listar recebimentos",
            description = "Ordenacao default: dataRecebimento DESC. Restrito a FINANCEIRO/ADMIN. Sem"
                    + " paginacao nesta sprint — volumes pequenos em dev-local; Sprint 13 ou Epic 15"
                    + " (AWS multi-instance) adicionara Pageable + filtros (contratoId, intervalo, etc.).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de recebimentos"),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role FINANCEIRO/ADMIN",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<List<RecebimentoResponse>> listarRecebimentos() {
        return ResponseEntity.ok(mapper.toRecebimentoListResponse(consultarRecebimentosUseCase.listar()));
    }

    // ============== helpers ==============

    private void garantirOwnershipOuOperador(UUID contratoId, UsuarioAutenticado principal) {
        if (operadorInterno(principal)) {
            return;
        }
        UUID tomadorId = contratoQueryPort
                .tomadorIdDoContrato(contratoId)
                .orElseThrow(() -> new CobrancaOwnershipException(contratoId));
        if (!tomadorId.equals(principal.id())) {
            throw new CobrancaOwnershipException(contratoId);
        }
    }

    /**
     * Valida que a Idempotency-Key respeita {@code [A-Za-z0-9._-]{1,100}} — recusa espacos,
     * caracteres unicode/controle e strings longas demais que poderiam causar truncamento ou
     * comportamento divergente da UNIQUE constraint do DB.
     */
    private static void validarIdempotencyKey(String key) {
        if (key == null || !IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
            throw new com.dynamis.sep_api.shared.exception.ValidacaoException(
                    "COB-400-001", "Header 'Idempotency-Key' invalido: aceita ate 100 caracteres em [A-Za-z0-9._-]");
        }
    }

    private boolean operadorInterno(UsuarioAutenticado principal) {
        Role role = principal.role();
        return role == Role.ADMIN || role == Role.FINANCEIRO;
    }
}
