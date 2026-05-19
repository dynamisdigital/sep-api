package com.dynamis.sep_api.credito.web.controller;

import com.dynamis.sep_api.credito.application.dto.IniciarConsentimentoOpenFinanceCommand;
import com.dynamis.sep_api.credito.application.usecase.IniciarConsentimentoOpenFinanceUseCase;
import com.dynamis.sep_api.credito.domain.exception.ConsentimentoNaoEncontradoException;
import com.dynamis.sep_api.credito.domain.exception.OwnershipPropostaException;
import com.dynamis.sep_api.credito.domain.exception.PropostaNaoEncontradaException;
import com.dynamis.sep_api.credito.domain.model.ConsentimentoOpenFinance;
import com.dynamis.sep_api.credito.domain.model.MovimentacaoOpenFinance;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.infrastructure.persistence.ConsentimentoOpenFinanceRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.MovimentacaoOpenFinanceRepository;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credito.web.dto.IniciarConsentimentoOpenFinanceRequest;
import com.dynamis.sep_api.credito.web.dto.IniciarConsentimentoOpenFinanceResponse;
import com.dynamis.sep_api.credito.web.dto.MovimentacaoConsolidadaResponse;
import com.dynamis.sep_api.credito.web.dto.OpenFinanceStatusResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoints REST do ciclo Open Finance do modulo {@code credito} (Sprint 9 Task 9.6).
 *
 * <ul>
 *   <li>{@code POST /api/v1/credito/propostas/{id}/open-finance/consentimento} — apenas
 *       CLIENTE dono;
 *   <li>{@code GET /api/v1/credito/propostas/{id}/open-finance} — CLIENTE dono ou
 *       FINANCEIRO/ADMIN.
 * </ul>
 *
 * <p>Acessa repositorios diretamente apenas pra montagem de view {@code GET} (leitura). Para
 * mutacoes ({@code POST}) delega ao {@link IniciarConsentimentoOpenFinanceUseCase}.
 */
@RestController
@RequestMapping("/api/v1/credito/propostas/{id}/open-finance")
@Tag(name = "credito", description = "Open Finance Brasil — consentimento + snapshot bancario")
public class OpenFinanceController {

    private final IniciarConsentimentoOpenFinanceUseCase iniciarUseCase;
    private final PropostaCreditoRepository propostaRepository;
    private final ConsentimentoOpenFinanceRepository consentimentoRepository;
    private final MovimentacaoOpenFinanceRepository movimentacaoRepository;

    public OpenFinanceController(
            IniciarConsentimentoOpenFinanceUseCase iniciarUseCase,
            PropostaCreditoRepository propostaRepository,
            ConsentimentoOpenFinanceRepository consentimentoRepository,
            MovimentacaoOpenFinanceRepository movimentacaoRepository) {
        this.iniciarUseCase = iniciarUseCase;
        this.propostaRepository = propostaRepository;
        this.consentimentoRepository = consentimentoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
    }

    @PostMapping("/consentimento")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(
            summary = "Iniciar consentimento Open Finance",
            description =
                    "Apenas CLIENTE dono da proposta. Gera URL de autorizacao Open Finance Brasil pra o tomador autorizar acesso a movimentacao bancaria. Persistencia local em PENDENTE antes de chamar provider (anti-orphan).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Consentimento iniciado",
                content = @Content(schema = @Schema(implementation = IniciarConsentimentoOpenFinanceResponse.class))),
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
                description = "Sem role CLIENTE ou proposta de outro tomador",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Proposta nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Ja existe consentimento PENDENTE pra esta proposta",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "422",
                description = "Proposta em status incompativel (APROVADA/REJEITADA)",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<IniciarConsentimentoOpenFinanceResponse> iniciarConsentimento(
            @PathVariable UUID id,
            @Valid @RequestBody IniciarConsentimentoOpenFinanceRequest request,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        ConsentimentoOpenFinance consentimento = iniciarUseCase.executar(new IniciarConsentimentoOpenFinanceCommand(
                id, principal.id(), request.cpfCnpjTomador(), request.redirectUri()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new IniciarConsentimentoOpenFinanceResponse(
                        consentimento.getId(),
                        consentimento.getStatus(),
                        consentimento.getUrlAutorizacao(),
                        consentimento.getDataExpiracao()));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Consultar status Open Finance",
            description =
                    "Cliente dono ve apenas propria proposta; FINANCEIRO/ADMIN ve qualquer. Retorna ultimo consentimento + snapshot consolidado quando autorizado.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Status atual",
                content = @Content(schema = @Schema(implementation = OpenFinanceStatusResponse.class))),
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
                description = "Proposta ou consentimento nao encontrados",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<OpenFinanceStatusResponse> consultarStatus(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        PropostaCredito proposta =
                propostaRepository.findById(id).orElseThrow(() -> new PropostaNaoEncontradaException(id));

        if (!operadorInterno(principal) && !proposta.getTomadorId().equals(principal.id())) {
            throw new OwnershipPropostaException("Proposta pertence a outro tomador");
        }

        ConsentimentoOpenFinance consentimento = consentimentoRepository
                .findFirstByPropostaIdOrderByDataInicioDesc(id)
                .orElseThrow(() -> new ConsentimentoNaoEncontradoException("proposta=" + id));

        MovimentacaoConsolidadaResponse mov = movimentacaoRepository
                .findByConsentimentoId(consentimento.getId())
                .map(OpenFinanceController::toMovimentacaoResponse)
                .orElse(null);

        return ResponseEntity.ok(new OpenFinanceStatusResponse(
                consentimento.getStatus(),
                consentimento.getDataInicio(),
                consentimento.getDataAutorizacao(),
                consentimento.getDataExpiracao(),
                mov));
    }

    private static MovimentacaoConsolidadaResponse toMovimentacaoResponse(MovimentacaoOpenFinance m) {
        return new MovimentacaoConsolidadaResponse(
                m.getMediaEntradasMensal(),
                m.getMediaSaidasMensal(),
                m.getSaldoMedio(),
                m.getNumeroMesesAvaliados(),
                m.getDataRecebimento());
    }

    private boolean operadorInterno(UsuarioAutenticado principal) {
        Role role = principal.role();
        return role == Role.ADMIN || role == Role.FINANCEIRO;
    }
}
