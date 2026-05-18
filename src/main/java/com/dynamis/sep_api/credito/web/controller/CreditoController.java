package com.dynamis.sep_api.credito.web.controller;

import com.dynamis.sep_api.credito.application.dto.CriarPropostaCreditoCommand;
import com.dynamis.sep_api.credito.application.usecase.ConsultarPropostaUseCase;
import com.dynamis.sep_api.credito.application.usecase.CriarPropostaCreditoUseCase;
import com.dynamis.sep_api.credito.application.usecase.ListarPropostasUseCase;
import com.dynamis.sep_api.credito.application.usecase.RegistrarParecerUseCase;
import com.dynamis.sep_api.credito.domain.exception.OwnershipPropostaException;
import com.dynamis.sep_api.credito.domain.model.ParecerCredito;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.infrastructure.persistence.ParecerCreditoRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.RegraCreditoAvaliadaRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.ScoreInternoRepository;
import com.dynamis.sep_api.credito.web.dto.CriarPropostaRequest;
import com.dynamis.sep_api.credito.web.dto.ParecerCreditoResponse;
import com.dynamis.sep_api.credito.web.dto.PropostaResponse;
import com.dynamis.sep_api.credito.web.dto.RegistrarParecerRequest;
import com.dynamis.sep_api.credito.web.dto.RegraAvaliadaResponse;
import com.dynamis.sep_api.credito.web.dto.ScoreInternoResponse;
import com.dynamis.sep_api.credito.web.mapper.CreditoWebMapper;
import com.dynamis.sep_api.identity.infrastructure.security.RequireStepUp;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints REST do modulo {@code credito} (Sprint 8 Task 8.5).
 *
 * <ul>
 *   <li>{@code POST /api/v1/credito/propostas} — cliente autenticado cria proposta;
 *   <li>{@code GET /api/v1/credito/propostas/{id}} — ownership ou FINANCEIRO/ADMIN;
 *   <li>{@code GET /api/v1/credito/propostas} — cliente lista proprias; FINANCEIRO/ADMIN
 *       filtra qualquer;
 *   <li>{@code POST /api/v1/credito/propostas/{id}/parecer} — FINANCEIRO + step-up;
 *   <li>{@code GET /api/v1/credito/propostas/{id}/regras} — FINANCEIRO ou ADMIN.
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/credito")
@Tag(name = "credito", description = "Analise de credito interna — propostas, motor de regras e parecer manual")
public class CreditoController {

    private final CriarPropostaCreditoUseCase criarPropostaUseCase;
    private final ConsultarPropostaUseCase consultarPropostaUseCase;
    private final ListarPropostasUseCase listarPropostasUseCase;
    private final RegistrarParecerUseCase registrarParecerUseCase;
    private final ScoreInternoRepository scoreRepository;
    private final ParecerCreditoRepository parecerRepository;
    private final RegraCreditoAvaliadaRepository regraRepository;
    private final CreditoWebMapper mapper;

    public CreditoController(
            CriarPropostaCreditoUseCase criarPropostaUseCase,
            ConsultarPropostaUseCase consultarPropostaUseCase,
            ListarPropostasUseCase listarPropostasUseCase,
            RegistrarParecerUseCase registrarParecerUseCase,
            ScoreInternoRepository scoreRepository,
            ParecerCreditoRepository parecerRepository,
            RegraCreditoAvaliadaRepository regraRepository,
            CreditoWebMapper mapper) {
        this.criarPropostaUseCase = criarPropostaUseCase;
        this.consultarPropostaUseCase = consultarPropostaUseCase;
        this.listarPropostasUseCase = listarPropostasUseCase;
        this.registrarParecerUseCase = registrarParecerUseCase;
        this.scoreRepository = scoreRepository;
        this.parecerRepository = parecerRepository;
        this.regraRepository = regraRepository;
        this.mapper = mapper;
    }

    @PostMapping("/propostas")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Criar proposta de credito",
            description = "Cliente autenticado cria proposta a partir de onboarding APROVADO_FINAL. ADMIN/FINANCEIRO"
                    + " nao criam proposta em nome de cliente nesta fase.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Proposta criada",
                content = @Content(schema = @Schema(implementation = PropostaResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Payload invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Onboarding pertence a outro tomador",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Onboarding nao encontrado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "422",
                description = "Onboarding nao esta APROVADO_FINAL",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<PropostaResponse> criar(
            @Valid @RequestBody CriarPropostaRequest request, @AuthenticationPrincipal UsuarioAutenticado principal) {
        PropostaCredito salva = criarPropostaUseCase.executar(new CriarPropostaCreditoCommand(
                principal.id(),
                request.solicitacaoOnboardingId(),
                request.tipoOperacao(),
                request.valorSolicitado(),
                request.prazoMeses()));
        PropostaResponse body = mapper.toResponse(salva, null, null);
        return ResponseEntity.created(URI.create("/api/v1/credito/propostas/" + salva.getId()))
                .body(body);
    }

    @GetMapping("/propostas/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Consultar proposta",
            description = "Cliente acessa apenas propria proposta; FINANCEIRO/ADMIN acessa qualquer.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Acesso negado (proposta de outro tomador)",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Proposta nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<PropostaResponse> consultar(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        PropostaCredito proposta = consultarPropostaUseCase.executar(id);
        if (!operadorInterno(principal) && !proposta.getTomadorId().equals(principal.id())) {
            throw new OwnershipPropostaException("Proposta pertence a outro tomador");
        }
        return ResponseEntity.ok(montarResponse(proposta));
    }

    @GetMapping("/propostas")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Listar propostas",
            description = "Cliente lista proprias propostas. FINANCEIRO/ADMIN podem filtrar por status e tomadorId.")
    public ResponseEntity<Page<PropostaResponse>> listar(
            @RequestParam(required = false) StatusProposta status,
            @RequestParam(required = false) UUID tomadorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PropostaCredito> propostas;
        if (operadorInterno(principal)) {
            propostas = listarPropostasUseCase.listarComFiltros(tomadorId, status, pageable);
        } else {
            propostas = listarPropostasUseCase.listarDoTomador(principal.id(), status, pageable);
        }
        return ResponseEntity.ok(propostas.map(this::montarResponse));
    }

    @PostMapping("/propostas/{id}/parecer")
    @PreAuthorize("hasRole('FINANCEIRO')")
    @RequireStepUp
    @Operation(
            summary = "Registrar parecer manual",
            description = "Restrito a FINANCEIRO + step-up. Decisao sobrepoe sugestao do motor.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Parecer registrado"),
        @ApiResponse(
                responseCode = "400",
                description = "Proposta em estado final ou justificativa invalida",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role FINANCEIRO ou step-up ausente",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Proposta nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ParecerCreditoResponse> registrarParecer(
            @PathVariable UUID id,
            @Valid @RequestBody RegistrarParecerRequest request,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        ParecerCredito parecer =
                registrarParecerUseCase.executar(id, principal.id(), request.decisao(), request.justificativa());
        return ResponseEntity.ok(mapper.toParecerResponse(parecer));
    }

    @GetMapping("/propostas/{id}/regras")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @Operation(
            summary = "Listar regras avaliadas",
            description = "Trilha auditavel de regras do motor — restrito a FINANCEIRO/ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de regras"),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Acesso negado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Proposta nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<List<RegraAvaliadaResponse>> listarRegras(@PathVariable UUID id) {
        consultarPropostaUseCase.executar(id); // garante existencia / 404
        List<RegraAvaliadaResponse> regras = regraRepository.findByPropostaIdOrderByDataAvaliacaoAsc(id).stream()
                .map(mapper::toRegraResponse)
                .toList();
        return ResponseEntity.ok(regras);
    }

    private PropostaResponse montarResponse(PropostaCredito proposta) {
        ScoreInternoResponse score = scoreRepository
                .findByPropostaId(proposta.getId())
                .map(mapper::toScoreResponse)
                .orElse(null);
        ParecerCreditoResponse parecer = parecerRepository
                .findTopByPropostaIdOrderByVersaoDesc(proposta.getId())
                .map(mapper::toParecerResponse)
                .orElse(null);
        return mapper.toResponse(proposta, score, parecer);
    }

    private boolean operadorInterno(UsuarioAutenticado principal) {
        Role role = principal.role();
        return role == Role.ADMIN || role == Role.FINANCEIRO;
    }
}
