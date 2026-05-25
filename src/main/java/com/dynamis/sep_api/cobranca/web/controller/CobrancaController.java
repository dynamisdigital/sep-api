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
    // Sprint 13 Task 13.7: 5 endpoints novos (inadimplencia + renegociacao).
    private final com.dynamis.sep_api.cobranca.application.usecase.ListarInadimplenciaUseCase
            listarInadimplenciaUseCase;
    private final com.dynamis.sep_api.cobranca.application.usecase.RegistrarContatoCobrancaUseCase
            registrarContatoUseCase;
    private final com.dynamis.sep_api.cobranca.application.usecase.IniciarRenegociacaoUseCase
            iniciarRenegociacaoUseCase;
    private final com.dynamis.sep_api.cobranca.application.usecase.AceitarRenegociacaoUseCase
            aceitarRenegociacaoUseCase;
    private final com.dynamis.sep_api.cobranca.application.usecase.RecusarRenegociacaoUseCase
            recusarRenegociacaoUseCase;
    private final com.dynamis.sep_api.cobranca.infrastructure.persistence.RenegociacaoRepository renegociacaoRepository;

    public CobrancaController(
            ConsultarAgendaPorContratoUseCase consultarAgendaUseCase,
            CalcularValorAtualizadoParcelaUseCase calcularValorAtualizadoUseCase,
            RegistrarRecebimentoUseCase registrarRecebimentoUseCase,
            ConsultarRecebimentosUseCase consultarRecebimentosUseCase,
            ContratoCobrancaQueryPort contratoQueryPort,
            CobrancaWebMapper mapper,
            com.dynamis.sep_api.cobranca.application.usecase.ListarInadimplenciaUseCase listarInadimplenciaUseCase,
            com.dynamis.sep_api.cobranca.application.usecase.RegistrarContatoCobrancaUseCase registrarContatoUseCase,
            com.dynamis.sep_api.cobranca.application.usecase.IniciarRenegociacaoUseCase iniciarRenegociacaoUseCase,
            com.dynamis.sep_api.cobranca.application.usecase.AceitarRenegociacaoUseCase aceitarRenegociacaoUseCase,
            com.dynamis.sep_api.cobranca.application.usecase.RecusarRenegociacaoUseCase recusarRenegociacaoUseCase,
            com.dynamis.sep_api.cobranca.infrastructure.persistence.RenegociacaoRepository renegociacaoRepository) {
        this.consultarAgendaUseCase = consultarAgendaUseCase;
        this.calcularValorAtualizadoUseCase = calcularValorAtualizadoUseCase;
        this.registrarRecebimentoUseCase = registrarRecebimentoUseCase;
        this.consultarRecebimentosUseCase = consultarRecebimentosUseCase;
        this.contratoQueryPort = contratoQueryPort;
        this.mapper = mapper;
        this.listarInadimplenciaUseCase = listarInadimplenciaUseCase;
        this.registrarContatoUseCase = registrarContatoUseCase;
        this.iniciarRenegociacaoUseCase = iniciarRenegociacaoUseCase;
        this.aceitarRenegociacaoUseCase = aceitarRenegociacaoUseCase;
        this.recusarRenegociacaoUseCase = recusarRenegociacaoUseCase;
        this.renegociacaoRepository = renegociacaoRepository;
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

    // ============================================================================
    // Sprint 13 Task 13.7 — inadimplencia + renegociacao
    // ============================================================================

    @GetMapping("/inadimplencia")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @Operation(
            summary = "Lista parcelas em inadimplencia",
            description =
                    "Triagem do backoffice/financeiro — parcelas ATRASADA ou INADIMPLENTE com filtros opcionais de dias de atraso.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista (pode estar vazia)."),
        @ApiResponse(responseCode = "401", description = "Sem autenticacao."),
        @ApiResponse(responseCode = "403", description = "Sem role financeiro/admin.")
    })
    public ResponseEntity<List<com.dynamis.sep_api.cobranca.web.dto.InadimplenciaResponse>> listarInadimplencia(
            @org.springframework.web.bind.annotation.RequestParam(value = "dias_atraso_min", required = false)
                    Integer diasMin,
            @org.springframework.web.bind.annotation.RequestParam(value = "dias_atraso_max", required = false)
                    Integer diasMax,
            @org.springframework.web.bind.annotation.RequestParam(value = "status", required = false)
                    java.util.Set<com.dynamis.sep_api.cobranca.domain.vo.StatusParcela> statusFiltro) {
        var filtro = new com.dynamis.sep_api.cobranca.application.usecase.ListarInadimplenciaUseCase.Filtro(
                statusFiltro, diasMin, diasMax);
        var linhas = listarInadimplenciaUseCase.listar(filtro);
        var resposta = linhas.stream()
                .map(l -> new com.dynamis.sep_api.cobranca.web.dto.InadimplenciaResponse(
                        l.parcela().getId(),
                        l.parcela().getAgenda().getId(),
                        l.contratoId(),
                        l.tomadorId(),
                        l.parcela().getNumero(),
                        l.parcela().getStatus(),
                        l.parcela().getDataVencimento(),
                        l.diasAtraso(),
                        l.parcela().valorTotal()))
                .toList();
        return ResponseEntity.ok(resposta);
    }

    @PostMapping("/parcelas/{id}/contato")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @Operation(
            summary = "Registra contato manual",
            description =
                    "Financeiro/admin registra contato com o tomador (telefonema, mensagem, presencial). Nao altera status da parcela.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "EventoCobranca criado."),
        @ApiResponse(responseCode = "400", description = "Payload invalido."),
        @ApiResponse(responseCode = "401", description = "Sem autenticacao."),
        @ApiResponse(responseCode = "403", description = "Sem role financeiro/admin."),
        @ApiResponse(
                responseCode = "404",
                description = "Parcela nao encontrada.",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<com.dynamis.sep_api.cobranca.web.dto.EventoCobrancaResponse> registrarContato(
            @PathVariable UUID id,
            @Valid @RequestBody com.dynamis.sep_api.cobranca.web.dto.RegistrarContatoRequest request,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        var evento = registrarContatoUseCase.executar(id, principal.id(), request.diasAtraso(), request.descricao());
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(com.dynamis.sep_api.cobranca.web.dto.EventoCobrancaResponse.from(evento));
    }

    @PostMapping("/parcelas/{id}/renegociacao")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @com.dynamis.sep_api.identity.infrastructure.security.RequireStepUp
    @Operation(
            summary = "Propoe renegociacao",
            description =
                    "Financeiro/admin propoe nova condicao pra parcela atrasada/inadimplente. Exige step-up. Parcela vai pra EM_NEGOCIACAO; expira em 7 dias.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Proposta criada."),
        @ApiResponse(responseCode = "400", description = "Payload invalido."),
        @ApiResponse(responseCode = "401", description = "Sem autenticacao."),
        @ApiResponse(responseCode = "403", description = "Sem role financeiro/admin ou step-up ausente."),
        @ApiResponse(responseCode = "404", description = "Parcela nao encontrada."),
        @ApiResponse(responseCode = "409", description = "Ja existe renegociacao ativa pra parcela.")
    })
    public ResponseEntity<com.dynamis.sep_api.cobranca.web.dto.RenegociacaoResponse> proporRenegociacao(
            @PathVariable UUID id,
            @Valid @RequestBody com.dynamis.sep_api.cobranca.web.dto.IniciarRenegociacaoRequest request,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        var renegociacao = iniciarRenegociacaoUseCase.executar(request.toCommand(id, principal.id()));
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(com.dynamis.sep_api.cobranca.web.dto.RenegociacaoResponse.from(renegociacao));
    }

    @org.springframework.web.bind.annotation.PatchMapping("/renegociacoes/{id}/aceite")
    @PreAuthorize("isAuthenticated()")
    @com.dynamis.sep_api.identity.infrastructure.security.RequireStepUp
    @Operation(
            summary = "Aceita renegociacao",
            description =
                    "Tomador aceita a proposta. Exige ownership + step-up. Gera AgendaPagamento substituta; parcela vira RENEGOCIADA.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Renegociacao aceita."),
        @ApiResponse(responseCode = "401", description = "Sem autenticacao."),
        @ApiResponse(
                responseCode = "403",
                description = "Owner invalido ou step-up ausente.",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Renegociacao nao encontrada."),
        @ApiResponse(responseCode = "409", description = "Renegociacao ja decidida ou expirada.")
    })
    public ResponseEntity<com.dynamis.sep_api.cobranca.web.dto.RenegociacaoResponse> aceitarRenegociacao(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        var renegociacao = aceitarRenegociacaoUseCase.executar(id, principal.id());
        return ResponseEntity.ok(com.dynamis.sep_api.cobranca.web.dto.RenegociacaoResponse.from(renegociacao));
    }

    /**
     * Step-up assimetrico vs aceite (spec 13.6 — fix code review): recusa NAO gera obrigacao
     * financeira nova; apenas reverte status. Aceite cria nova {@code AgendaPagamento} substituta
     * com novas condicoes, entao exige confirmacao reforçada (step-up). Documentado aqui pra
     * evitar drift entre spec e codigo.
     */
    @org.springframework.web.bind.annotation.PatchMapping("/renegociacoes/{id}/recusa")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Recusa renegociacao",
            description =
                    "Tomador recusa a proposta. Apenas ownership (sem step-up — recusa nao gera obrigacao financeira nova). Parcela volta ao status anterior (ATRASADA/INADIMPLENTE).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Renegociacao recusada."),
        @ApiResponse(responseCode = "401", description = "Sem autenticacao."),
        @ApiResponse(
                responseCode = "403",
                description = "Owner invalido.",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Renegociacao nao encontrada."),
        @ApiResponse(responseCode = "409", description = "Renegociacao ja decidida ou expirada.")
    })
    public ResponseEntity<com.dynamis.sep_api.cobranca.web.dto.RenegociacaoResponse> recusarRenegociacao(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        var renegociacao = recusarRenegociacaoUseCase.executar(id, principal.id());
        return ResponseEntity.ok(com.dynamis.sep_api.cobranca.web.dto.RenegociacaoResponse.from(renegociacao));
    }
}
