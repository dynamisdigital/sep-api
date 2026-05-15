package com.dynamis.sep_api.onboarding.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.onboarding.application.dto.DocumentoUploadCommand;
import com.dynamis.sep_api.onboarding.application.dto.StatusOnboardingEmpresaView;
import com.dynamis.sep_api.onboarding.application.usecase.ConsultarRepresentantesLegaisUseCase;
import com.dynamis.sep_api.onboarding.application.usecase.ConsultarStatusOnboardingEmpresaUseCase;
import com.dynamis.sep_api.onboarding.application.usecase.EnviarDocumentoUseCase;
import com.dynamis.sep_api.onboarding.application.usecase.IniciarOnboardingEmpresaUseCase;
import com.dynamis.sep_api.onboarding.application.usecase.IniciarVerificacaoKybUseCase;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
import com.dynamis.sep_api.onboarding.web.dto.EmpresaResponse;
import com.dynamis.sep_api.onboarding.web.dto.IniciarOnboardingEmpresaRequest;
import com.dynamis.sep_api.onboarding.web.dto.RepresentanteLegalResponse;
import com.dynamis.sep_api.onboarding.web.dto.StatusOnboardingEmpresaResponse;
import com.dynamis.sep_api.onboarding.web.mapper.OnboardingEmpresaWebMapper;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import com.dynamis.sep_api.shared.integration.CorrelationIdFilter;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/onboarding/empresa")
@Tag(name = "onboarding", description = "Onboarding KYB Pessoa Juridica (Resolucao CMN 4.656/2018)")
public class OnboardingEmpresaController {

    private static final String CODIGO_ARQUIVO_INVALIDO = "ONB-400-007";

    private final IniciarOnboardingEmpresaUseCase iniciarUseCase;
    private final EnviarDocumentoUseCase enviarDocumentoUseCase;
    private final IniciarVerificacaoKybUseCase iniciarVerificacaoUseCase;
    private final ConsultarStatusOnboardingEmpresaUseCase consultarStatusUseCase;
    private final ConsultarRepresentantesLegaisUseCase consultarRepresentantesUseCase;
    private final OnboardingEmpresaWebMapper mapper;

    public OnboardingEmpresaController(
            IniciarOnboardingEmpresaUseCase iniciarUseCase,
            EnviarDocumentoUseCase enviarDocumentoUseCase,
            IniciarVerificacaoKybUseCase iniciarVerificacaoUseCase,
            ConsultarStatusOnboardingEmpresaUseCase consultarStatusUseCase,
            ConsultarRepresentantesLegaisUseCase consultarRepresentantesUseCase,
            OnboardingEmpresaWebMapper mapper) {
        this.iniciarUseCase = iniciarUseCase;
        this.enviarDocumentoUseCase = enviarDocumentoUseCase;
        this.iniciarVerificacaoUseCase = iniciarVerificacaoUseCase;
        this.consultarStatusUseCase = consultarStatusUseCase;
        this.consultarRepresentantesUseCase = consultarRepresentantesUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Iniciar solicitacao KYB PJ")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Solicitacao PJ criada"),
        @ApiResponse(
                responseCode = "400",
                description = "Validacao (CNPJ/razao social) falhou",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "CNPJ ja possui solicitacao ativa",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<EmpresaResponse> iniciar(
            @Valid @RequestBody IniciarOnboardingEmpresaRequest body,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        SolicitacaoOnboarding salva = iniciarUseCase.executar(
                principal.id(),
                body.cnpj(),
                body.razaoSocial(),
                body.nomeFantasia(),
                body.tipoSocietario(),
                body.porte());
        EmpresaResponse response = mapper.toEmpresaResponse(salva);
        return ResponseEntity.created(URI.create("/api/v1/onboarding/empresa/" + salva.getId()))
                .body(response);
    }

    @PostMapping(path = "/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Anexar documento PJ (contrato social, CCMEI, comprovante de endereco)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Documento anexado"),
        @ApiResponse(
                responseCode = "400",
                description = "MIME nao suportado, tamanho > 10MB ou status invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente"),
        @ApiResponse(responseCode = "403", description = "Nao e dono da solicitacao"),
        @ApiResponse(responseCode = "404", description = "Solicitacao nao encontrada")
    })
    public ResponseEntity<Void> enviarDocumento(
            @PathVariable UUID id,
            @Parameter(example = "CONTRATO_SOCIAL") @RequestParam("tipo") TipoDocumento tipo,
            @RequestParam("arquivo") MultipartFile arquivo,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ValidacaoException(CODIGO_ARQUIVO_INVALIDO, "Arquivo do documento e obrigatorio");
        }
        DocumentoUploadCommand cmd;
        try {
            cmd = new DocumentoUploadCommand(
                    tipo, arquivo.getContentType(), arquivo.getOriginalFilename(), arquivo.getBytes());
        } catch (IOException ex) {
            throw new ValidacaoException(CODIGO_ARQUIVO_INVALIDO, "Falha ao ler bytes do arquivo");
        }
        boolean isAdmin = principal.role() == Role.ADMIN;
        enviarDocumentoUseCase.executar(id, principal.id(), isAdmin, cmd);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/verificar")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Disparar verificacao KYB no provider externo")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Verificacao disparada (PLD orquestra apos KYB APROVADO)"),
        @ApiResponse(
                responseCode = "400",
                description = "Documentos minimos PJ ausentes, tipo invalido ou status invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente"),
        @ApiResponse(responseCode = "403", description = "Nao e dono"),
        @ApiResponse(responseCode = "404", description = "Solicitacao ou KYB nao encontrado")
    })
    public ResponseEntity<Void> disparar(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        boolean isAdmin = principal.role() == Role.ADMIN;
        iniciarVerificacaoUseCase.executar(id, principal.id(), isAdmin, correlationId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar status de uma solicitacao PJ")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status PJ retornado"),
        @ApiResponse(responseCode = "401", description = "Token ausente"),
        @ApiResponse(responseCode = "403", description = "Nao e dono nem ADMIN"),
        @ApiResponse(responseCode = "404", description = "Solicitacao ou KYB nao encontrado")
    })
    public ResponseEntity<StatusOnboardingEmpresaResponse> consultar(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        boolean isAdmin = principal.role() == Role.ADMIN;
        StatusOnboardingEmpresaView view = consultarStatusUseCase.executar(id, principal.id(), isAdmin);
        return ResponseEntity.ok(mapper.toStatusResponse(view));
    }

    @GetMapping("/{id}/representantes")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar representantes legais da empresa")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de representantes retornada"),
        @ApiResponse(responseCode = "401", description = "Token ausente"),
        @ApiResponse(responseCode = "403", description = "Nao e dono nem ADMIN"),
        @ApiResponse(responseCode = "404", description = "Solicitacao ou KYB nao encontrado")
    })
    public ResponseEntity<List<RepresentanteLegalResponse>> listarRepresentantes(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        boolean isAdmin = principal.role() == Role.ADMIN;
        var representantes = consultarRepresentantesUseCase.executar(id, principal.id(), isAdmin).stream()
                .map(mapper::toRepresentanteResponse)
                .toList();
        return ResponseEntity.ok(representantes);
    }
}
