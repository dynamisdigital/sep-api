package com.dynamis.sep_api.contratos.web.controller;

import com.dynamis.sep_api.contratos.application.usecase.BaixarDocumentoAssinadoUseCase;
import com.dynamis.sep_api.contratos.application.usecase.CancelarContratoUseCase;
import com.dynamis.sep_api.contratos.application.usecase.ConsultarContratoUseCase;
import com.dynamis.sep_api.contratos.application.usecase.ConsultarStatusAssinaturaUseCase;
import com.dynamis.sep_api.contratos.application.usecase.EnviarParaAssinaturaUseCase;
import com.dynamis.sep_api.contratos.application.usecase.RegistrarAceiteUseCase;
import com.dynamis.sep_api.contratos.application.usecase.command.CancelarContratoCommand;
import com.dynamis.sep_api.contratos.application.usecase.command.RegistrarAceiteCommand;
import com.dynamis.sep_api.contratos.domain.exception.ContratoOwnershipException;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.web.dto.CancelarContratoRequest;
import com.dynamis.sep_api.contratos.web.dto.ContratoResponse;
import com.dynamis.sep_api.contratos.web.dto.EnviarAssinaturaRequest;
import com.dynamis.sep_api.contratos.web.dto.RegistrarAceiteRequest;
import com.dynamis.sep_api.contratos.web.dto.StatusAssinaturaResponse;
import com.dynamis.sep_api.contratos.web.dto.VersaoContratoResponse;
import com.dynamis.sep_api.contratos.web.mapper.ContratoWebMapper;
import com.dynamis.sep_api.identity.infrastructure.security.RequireStepUpEstrito;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints REST do modulo {@code contratos} (Sprint 10 Task 10.6).
 *
 * <ul>
 *   <li>{@code GET /api/v1/contratos/proposta/{propostaId}} — ownership ou FINANCEIRO/ADMIN;
 *   <li>{@code GET /api/v1/contratos/{id}} — ownership ou FINANCEIRO/ADMIN;
 *   <li>{@code GET /api/v1/contratos/{id}/versoes} — ownership ou FINANCEIRO/ADMIN;
 *   <li>{@code PATCH /api/v1/contratos/{id}/aceite} — CLIENTE (ownership) + step-up estrito;
 *   <li>{@code POST /api/v1/contratos/{id}/cancelar} — FINANCEIRO/ADMIN + step-up estrito.
 * </ul>
 *
 * <p>Controller depende apenas dos use cases da camada {@code application} — nao acessa
 * repository JPA diretamente (preserva fronteira Hexagonal/DDD do PRD §11).
 *
 * <p>Aceite, cancelamento e envio para assinatura sao atos legais sobre documento contratual
 * (CMN 4.656/2018 Art. 11) e usam {@code @RequireStepUpEstrito} (Sprint 27): exigem MFA ativo e
 * step-up token valido, sem o bypass de migracao pre-MFA do {@code @RequireStepUp} legado.
 */
@RestController
@RequestMapping("/api/v1/contratos")
@Tag(name = "contratos", description = "Formalizacao contratual — geracao, aceite e cancelamento pre-aceite")
public class ContratoController {

    private final ConsultarContratoUseCase consultarContratoUseCase;
    private final RegistrarAceiteUseCase registrarAceiteUseCase;
    private final CancelarContratoUseCase cancelarContratoUseCase;
    private final EnviarParaAssinaturaUseCase enviarParaAssinaturaUseCase;
    private final ConsultarStatusAssinaturaUseCase consultarStatusAssinaturaUseCase;
    private final BaixarDocumentoAssinadoUseCase baixarDocumentoAssinadoUseCase;
    private final ContratoWebMapper mapper;

    public ContratoController(
            ConsultarContratoUseCase consultarContratoUseCase,
            RegistrarAceiteUseCase registrarAceiteUseCase,
            CancelarContratoUseCase cancelarContratoUseCase,
            EnviarParaAssinaturaUseCase enviarParaAssinaturaUseCase,
            ConsultarStatusAssinaturaUseCase consultarStatusAssinaturaUseCase,
            BaixarDocumentoAssinadoUseCase baixarDocumentoAssinadoUseCase,
            ContratoWebMapper mapper) {
        this.consultarContratoUseCase = consultarContratoUseCase;
        this.registrarAceiteUseCase = registrarAceiteUseCase;
        this.cancelarContratoUseCase = cancelarContratoUseCase;
        this.enviarParaAssinaturaUseCase = enviarParaAssinaturaUseCase;
        this.consultarStatusAssinaturaUseCase = consultarStatusAssinaturaUseCase;
        this.baixarDocumentoAssinadoUseCase = baixarDocumentoAssinadoUseCase;
        this.mapper = mapper;
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Consultar contrato por id",
            description = "Cliente acessa apenas contrato proprio; FINANCEIRO/ADMIN acessa qualquer.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "OK",
                content = @Content(schema = @Schema(implementation = ContratoResponse.class))),
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
                description = "Contrato nao encontrado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ContratoResponse> consultarPorId(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        Contrato contrato = consultarContratoUseCase.porId(id);
        garantirOwnershipOuOperador(contrato, principal);
        return ResponseEntity.ok(mapper.toResponse(
                contrato,
                consultarContratoUseCase.buscarAceiteDaVersaoVigente(contrato).orElse(null)));
    }

    @GetMapping("/proposta/{propostaId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Consultar contrato pela proposta",
            description = "Atalho que dispensa conhecer o contratoId quando se sabe a proposta. Ownership ou"
                    + " FINANCEIRO/ADMIN.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "OK",
                content = @Content(schema = @Schema(implementation = ContratoResponse.class))),
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
                description = "Proposta sem contrato gerado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ContratoResponse> consultarPorProposta(
            @PathVariable UUID propostaId, @AuthenticationPrincipal UsuarioAutenticado principal) {
        Contrato contrato = consultarContratoUseCase.porPropostaId(propostaId);
        garantirOwnershipOuOperador(contrato, principal);
        return ResponseEntity.ok(mapper.toResponse(
                contrato,
                consultarContratoUseCase.buscarAceiteDaVersaoVigente(contrato).orElse(null)));
    }

    @GetMapping("/{id}/versoes")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Listar versoes geradas do contrato",
            description = "Versoes em ordem ascendente de numero. Ownership ou FINANCEIRO/ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de versoes"),
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
                description = "Contrato nao encontrado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<List<VersaoContratoResponse>> listarVersoes(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        Contrato contrato = consultarContratoUseCase.porId(id);
        garantirOwnershipOuOperador(contrato, principal);
        return ResponseEntity.ok(mapper.toVersaoListResponse(consultarContratoUseCase.listarVersoes(id)));
    }

    @PatchMapping("/{id}/aceite")
    @PreAuthorize("hasRole('CLIENTE')")
    @RequireStepUpEstrito
    @Operation(
            summary = "Registrar aceite do tomador",
            description = "CLIENTE titular do contrato registra aceite sobre a versao vigente. Exige step-up"
                    + " estrito (X-Step-Up-Token + MFA ativo, sem bypass).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Aceite registrado"),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role CLIENTE, sem step-up, sem MFA ativo ou contrato de outro tomador",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Contrato nao encontrado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Contrato fora de AGUARDANDO_ACEITE ou versao ja aceita",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ContratoResponse> registrarAceite(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) RegistrarAceiteRequest request,
            @AuthenticationPrincipal UsuarioAutenticado principal,
            HttpServletRequest servletRequest) {
        Contrato contrato = registrarAceiteUseCase.executar(new RegistrarAceiteCommand(
                id, principal.id(), extrairIp(servletRequest), servletRequest.getHeader("User-Agent")));
        return ResponseEntity.ok(mapper.toResponse(
                contrato,
                consultarContratoUseCase.buscarAceiteDaVersaoVigente(contrato).orElse(null)));
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @RequireStepUpEstrito
    @Operation(
            summary = "Cancelar contrato pre-aceite",
            description = "FINANCEIRO ou ADMIN cancela contrato em GERADO/AGUARDANDO_ACEITE. Exige step-up"
                    + " estrito (X-Step-Up-Token + MFA ativo, sem bypass) e justificativa (10-500 chars).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contrato cancelado"),
        @ApiResponse(
                responseCode = "400",
                description = "Justificativa invalida",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role FINANCEIRO/ADMIN, sem step-up ou sem MFA ativo",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Contrato nao encontrado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Contrato ja aceito/em assinatura/cancelado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ContratoResponse> cancelar(
            @PathVariable UUID id,
            @Valid @RequestBody CancelarContratoRequest request,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        Contrato contrato = cancelarContratoUseCase.executar(
                new CancelarContratoCommand(id, principal.id(), request.justificativa()));
        return ResponseEntity.ok(mapper.toResponse(
                contrato,
                consultarContratoUseCase.buscarAceiteDaVersaoVigente(contrato).orElse(null)));
    }

    @PostMapping("/{id}/assinar")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @RequireStepUpEstrito
    @Operation(
            summary = "Enviar contrato para assinatura digital",
            description = "FINANCEIRO/ADMIN dispara envio manual ao provider (Clicksign). Idempotente:"
                    + " envelope existente para a versao vigente eh devolvido sem chamar o provider novamente."
                    + " Disparo automatico apos aceite ocorre via ContratoAceitoListener (Sprint 11 Task 11.5);"
                    + " este endpoint serve para reprocessamento quando o auto-envio falhar (runbook em CONTRATOS.md).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "202",
                description = "Envio aceito; envelope criado ou idempotente",
                content = @Content(schema = @Schema(implementation = StatusAssinaturaResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Body malformado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role FINANCEIRO/ADMIN, sem step-up ou sem MFA ativo",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Contrato nao encontrado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Contrato fora de ACEITO/EM_ASSINATURA",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "422",
                description = "Falha na geracao da CCB ou provider 4xx (contrato/permissao)",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "502",
                description = "Provider de assinatura retornou resposta nao processavel",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "503",
                description = "Provider de assinatura indisponivel (5xx); reagendar reprocessamento",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<StatusAssinaturaResponse> enviarParaAssinatura(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) EnviarAssinaturaRequest request,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        enviarParaAssinaturaUseCase.executar(id, "manual:" + principal.id() + ":" + id);
        return ResponseEntity.accepted()
                .body(mapper.toStatusAssinaturaResponse(consultarStatusAssinaturaUseCase.executar(id)));
    }

    @GetMapping("/{id}/assinatura/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Consultar status da assinatura",
            description = "Snapshot do envelope (statusEnvelope) + status do contrato. Fonte: estado local;"
                    + " webhook (Task 11.6) eh fonte de verdade operacional. Ownership ou FINANCEIRO/ADMIN.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Status retornado",
                content = @Content(schema = @Schema(implementation = StatusAssinaturaResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Path param invalido",
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
                description = "Contrato nao encontrado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<StatusAssinaturaResponse> consultarStatusAssinatura(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        Contrato contrato = consultarContratoUseCase.porId(id);
        garantirOwnershipOuOperador(contrato, principal);
        return ResponseEntity.ok(mapper.toStatusAssinaturaResponse(consultarStatusAssinaturaUseCase.executar(id)));
    }

    @GetMapping("/{id}/documento-assinado")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Baixar PDF assinado",
            description = "Retorna o PDF assinado armazenado pelo SEP (BYTEA inline; Epic 16 troca por S3/MinIO).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "PDF assinado",
                content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
        @ApiResponse(
                responseCode = "400",
                description = "Path param invalido",
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
                description = "Contrato nao encontrado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Contrato ainda nao assinado ou documento indisponivel",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<byte[]> baixarDocumentoAssinado(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioAutenticado principal,
            HttpServletRequest servletRequest) {
        Contrato contrato = consultarContratoUseCase.porId(id);
        garantirOwnershipOuOperador(contrato, principal);
        BaixarDocumentoAssinadoUseCase.Resultado resultado = baixarDocumentoAssinadoUseCase.executar(
                id, principal.id(), extrairIp(servletRequest), servletRequest.getHeader("User-Agent"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"contrato-" + id + "-assinado.pdf\"")
                .header("X-Document-Hash-Sha256", resultado.documento().getHashSha256())
                .body(resultado.conteudo());
    }

    // ============== helpers ==============

    private void garantirOwnershipOuOperador(Contrato contrato, UsuarioAutenticado principal) {
        if (operadorInterno(principal)) {
            return;
        }
        if (!contrato.getTomadorId().equals(principal.id())) {
            throw new ContratoOwnershipException(contrato.getId());
        }
    }

    private boolean operadorInterno(UsuarioAutenticado principal) {
        return principal.temRole(Role.ADMIN) || principal.temRole(Role.FINANCEIRO);
    }

    /**
     * Extrai o IP do request. Usa {@code X-Forwarded-For} apenas se o servidor estiver atras de um
     * proxy confiavel — comportamento ainda nao configurado no projeto, entao caimos em {@code
     * getRemoteAddr()} pra evitar IP spoof via header arbitrario. Quando o reverse proxy AWS
     * entrar na Epic 16, este metodo ganha integracao com {@code ForwardedHeaderFilter}.
     */
    private static String extrairIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
