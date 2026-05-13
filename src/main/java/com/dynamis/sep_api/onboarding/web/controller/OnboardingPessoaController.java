package com.dynamis.sep_api.onboarding.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.onboarding.application.dto.DocumentoUploadCommand;
import com.dynamis.sep_api.onboarding.application.dto.StatusOnboardingView;
import com.dynamis.sep_api.onboarding.application.usecase.ConsultarStatusOnboardingUseCase;
import com.dynamis.sep_api.onboarding.application.usecase.EnviarDocumentoUseCase;
import com.dynamis.sep_api.onboarding.application.usecase.IniciarOnboardingPessoaUseCase;
import com.dynamis.sep_api.onboarding.application.usecase.IniciarVerificacaoKycUseCase;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
import com.dynamis.sep_api.onboarding.web.dto.IniciarOnboardingRequest;
import com.dynamis.sep_api.onboarding.web.dto.OnboardingResponse;
import com.dynamis.sep_api.onboarding.web.dto.StatusOnboardingResponse;
import com.dynamis.sep_api.onboarding.web.mapper.OnboardingWebMapper;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/onboarding/pessoa")
@Tag(name = "onboarding", description = "Onboarding KYC Pessoa Fisica (Resolucao CMN 4.656/2018)")
public class OnboardingPessoaController {

    private static final String CODIGO_ARQUIVO_INVALIDO = "ONB-400-007";

    private final IniciarOnboardingPessoaUseCase iniciarUseCase;
    private final EnviarDocumentoUseCase enviarDocumentoUseCase;
    private final IniciarVerificacaoKycUseCase iniciarVerificacaoUseCase;
    private final ConsultarStatusOnboardingUseCase consultarStatusUseCase;
    private final OnboardingWebMapper mapper;

    public OnboardingPessoaController(
            IniciarOnboardingPessoaUseCase iniciarUseCase,
            EnviarDocumentoUseCase enviarDocumentoUseCase,
            IniciarVerificacaoKycUseCase iniciarVerificacaoUseCase,
            ConsultarStatusOnboardingUseCase consultarStatusUseCase,
            OnboardingWebMapper mapper) {
        this.iniciarUseCase = iniciarUseCase;
        this.enviarDocumentoUseCase = enviarDocumentoUseCase;
        this.iniciarVerificacaoUseCase = iniciarVerificacaoUseCase;
        this.consultarStatusUseCase = consultarStatusUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Iniciar solicitacao KYC PF")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Solicitacao criada"),
        @ApiResponse(
                responseCode = "400",
                description = "Validacao (CPF/data) falhou",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente ou invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "CPF ja possui solicitacao ativa",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<OnboardingResponse> iniciar(
            @Valid @RequestBody IniciarOnboardingRequest body, @AuthenticationPrincipal UsuarioAutenticado principal) {
        SolicitacaoOnboarding salva =
                iniciarUseCase.executar(principal.id(), body.cpf(), body.nomeCompleto(), body.dataNascimento());
        OnboardingResponse response = mapper.toOnboardingResponse(salva);
        return ResponseEntity.created(URI.create("/api/v1/onboarding/pessoa/" + salva.getId()))
                .body(response);
    }

    @PostMapping(path = "/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Anexar documento cadastral (multipart)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Documento anexado"),
        @ApiResponse(
                responseCode = "400",
                description = "MIME nao suportado, tamanho > 10MB, ou status invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente"),
        @ApiResponse(responseCode = "403", description = "Nao e dono da solicitacao"),
        @ApiResponse(responseCode = "404", description = "Solicitacao nao encontrada")
    })
    public ResponseEntity<Void> enviarDocumento(
            @PathVariable UUID id,
            @Parameter(example = "RG") @RequestParam("tipo") TipoDocumento tipo,
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
    @Operation(summary = "Disparar verificacao KYC no provider externo")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Verificacao disparada (resultado vira por webhook)"),
        @ApiResponse(
                responseCode = "400",
                description = "Documentos minimos ausentes ou status invalido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente"),
        @ApiResponse(responseCode = "403", description = "Nao e dono"),
        @ApiResponse(responseCode = "404", description = "Solicitacao nao encontrada")
    })
    public ResponseEntity<Void> disparar(@PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        boolean isAdmin = principal.role() == Role.ADMIN;
        iniciarVerificacaoUseCase.executar(id, principal.id(), isAdmin, correlationId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar status de uma solicitacao")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status retornado"),
        @ApiResponse(responseCode = "401", description = "Token ausente"),
        @ApiResponse(responseCode = "403", description = "Nao e dono nem ADMIN"),
        @ApiResponse(responseCode = "404", description = "Solicitacao nao encontrada")
    })
    public ResponseEntity<StatusOnboardingResponse> consultar(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        boolean isAdmin = principal.role() == Role.ADMIN;
        StatusOnboardingView view = consultarStatusUseCase.executar(id, principal.id(), isAdmin);
        return ResponseEntity.ok(mapper.toStatusResponse(view));
    }
}
