package com.dynamis.sep_api.pix.web.controller;

import com.dynamis.sep_api.identity.infrastructure.security.RequireStepUpEstrito;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.pix.application.dto.CadastrarChavePixCommand;
import com.dynamis.sep_api.pix.application.dto.CadastrarChavePixResult;
import com.dynamis.sep_api.pix.application.usecase.CadastrarChavePixUseCase;
import com.dynamis.sep_api.pix.application.usecase.ListarChavesPixUseCase;
import com.dynamis.sep_api.pix.application.usecase.RemoverChavePixUseCase;
import com.dynamis.sep_api.pix.web.dto.CadastrarChavePixRequest;
import com.dynamis.sep_api.pix.web.dto.ChavePixResponse;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import com.dynamis.sep_api.shared.integration.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Gestao assistida de chaves Pix da conta operacional/escrow (Sprint 31 Task 31.7). Operacao
 * sensivel: mutacoes ({@code POST}/{@code DELETE}) exigem {@code FINANCEIRO}/{@code ADMIN} +
 * step-up estrito ({@link RequireStepUpEstrito}, sem bypass de MFA); a leitura e read-only para os
 * mesmos papeis, sem step-up. Nenhuma resposta ou erro expoe o valor da chave em claro.
 */
@RestController
@RequestMapping("/api/v1/pix/chaves")
@Tag(name = "pix-chaves", description = "Gestao assistida de chaves Pix da conta operacional")
public class PixChaveController {

    private final CadastrarChavePixUseCase cadastrarChave;
    private final ListarChavesPixUseCase listarChaves;
    private final RemoverChavePixUseCase removerChave;

    public PixChaveController(
            CadastrarChavePixUseCase cadastrarChave,
            ListarChavesPixUseCase listarChaves,
            RemoverChavePixUseCase removerChave) {
        this.cadastrarChave = cadastrarChave;
        this.listarChaves = listarChaves;
        this.removerChave = removerChave;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @RequireStepUpEstrito
    @Operation(
            summary = "Cadastrar chave Pix da conta operacional",
            description = "Cadastra a chave no provider e persiste apenas hash + mascara (o valor em claro nunca e"
                    + " armazenado ou retornado). Exige Idempotency-Key e X-Step-Up-Token (step-up estrito, sem"
                    + " bypass de MFA). Reapresentacao com a mesma key e mesmo tipo/valor retorna a chave existente"
                    + " (200); payload divergente ou chave equivalente ja ativa retornam 409.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Chave cadastrada"),
        @ApiResponse(responseCode = "200", description = "Retorno idempotente da chave existente"),
        @ApiResponse(
                responseCode = "400",
                description = "Idempotency-Key ausente ou tipo/valor invalido (sem ecoar o valor)",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role autorizada ou sem step-up valido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Idempotency-Key reutilizada com payload diferente ou chave equivalente ja ativa",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "422",
                description = "Conta operacional/escrow indisponivel",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ChavePixResponse> cadastrar(
            @Parameter(description = "Chave de idempotencia da operacao", required = true)
                    @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @Valid @RequestBody CadastrarChavePixRequest request,
            @AuthenticationPrincipal UsuarioAutenticado operador) {

        CadastrarChavePixResult resultado = cadastrarChave.executar(new CadastrarChavePixCommand(
                request.tipo(), request.valor(), idempotencyKey, operador.id(), MDC.get(CorrelationIdFilter.MDC_KEY)));

        HttpStatus status = resultado.novo() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ChavePixResponse.de(resultado));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @Operation(
            summary = "Listar chaves Pix da conta operacional",
            description = "Leitura local (nunca consulta o provider), sempre mascarada, incluindo chaves INATIVAS"
                    + " (historico), da mais recente para a mais antiga. Sem step-up.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lista de chaves (pode ser vazia)")})
    public ResponseEntity<List<ChavePixResponse>> listar() {
        List<ChavePixResponse> chaves =
                listarChaves.executar().stream().map(ChavePixResponse::de).toList();
        return ResponseEntity.ok(chaves);
    }

    @DeleteMapping("/{chaveId}")
    @PreAuthorize("hasAnyRole('FINANCEIRO','ADMIN')")
    @RequireStepUpEstrito
    @Operation(
            summary = "Remover (inativar) chave Pix da conta operacional",
            description = "Remocao logica ATIVA -> INATIVA apos remover no provider; o historico nao e apagado."
                    + " Exige X-Step-Up-Token (step-up estrito). Idempotente: remover chave ja INATIVA retorna 204"
                    + " sem novo efeito.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Chave removida (ou ja estava INATIVA)"),
        @ApiResponse(
                responseCode = "403",
                description = "Sem role autorizada ou sem step-up valido",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Chave nao encontrada (resposta neutra)",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> remover(
            @PathVariable UUID chaveId, @AuthenticationPrincipal UsuarioAutenticado operador) {
        removerChave.executar(chaveId, operador.id(), MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.noContent().build();
    }
}
