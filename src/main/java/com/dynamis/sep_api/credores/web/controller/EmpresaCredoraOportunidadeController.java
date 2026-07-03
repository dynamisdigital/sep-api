package com.dynamis.sep_api.credores.web.controller;

import com.dynamis.sep_api.credores.application.usecase.CancelarInteresseCredoraUseCase;
import com.dynamis.sep_api.credores.application.usecase.ConsultarInteresseAtivoCredoraUseCase;
import com.dynamis.sep_api.credores.application.usecase.ConsultarOportunidadeCredoraUseCase;
import com.dynamis.sep_api.credores.application.usecase.ListarOportunidadesCredoraUseCase;
import com.dynamis.sep_api.credores.application.usecase.RegistrarInteresseCredoraUseCase;
import com.dynamis.sep_api.credores.application.usecase.SincronizarOportunidadesInvestimentoUseCase;
import com.dynamis.sep_api.credores.web.dto.InteresseResponse;
import com.dynamis.sep_api.credores.web.dto.OportunidadeResponse;
import com.dynamis.sep_api.credores.web.dto.SincronizacaoOportunidadesResponse;
import com.dynamis.sep_api.credores.web.mapper.CarteiraCredoraWebMapper;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import com.dynamis.sep_api.shared.exception.ErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/credores/oportunidades")
@Tag(name = "credores-oportunidades", description = "Oportunidades de investimento e interesse da credora (Epic 10)")
public class EmpresaCredoraOportunidadeController {

    private final ListarOportunidadesCredoraUseCase listarUseCase;
    private final ConsultarOportunidadeCredoraUseCase consultarUseCase;
    private final RegistrarInteresseCredoraUseCase registrarInteresseUseCase;
    private final CancelarInteresseCredoraUseCase cancelarInteresseUseCase;
    private final ConsultarInteresseAtivoCredoraUseCase consultarInteresseAtivoUseCase;
    private final SincronizarOportunidadesInvestimentoUseCase sincronizarUseCase;
    private final CarteiraCredoraWebMapper mapper;

    public EmpresaCredoraOportunidadeController(
            ListarOportunidadesCredoraUseCase listarUseCase,
            ConsultarOportunidadeCredoraUseCase consultarUseCase,
            RegistrarInteresseCredoraUseCase registrarInteresseUseCase,
            CancelarInteresseCredoraUseCase cancelarInteresseUseCase,
            ConsultarInteresseAtivoCredoraUseCase consultarInteresseAtivoUseCase,
            SincronizarOportunidadesInvestimentoUseCase sincronizarUseCase,
            CarteiraCredoraWebMapper mapper) {
        this.listarUseCase = listarUseCase;
        this.consultarUseCase = consultarUseCase;
        this.registrarInteresseUseCase = registrarInteresseUseCase;
        this.cancelarInteresseUseCase = cancelarInteresseUseCase;
        this.consultarInteresseAtivoUseCase = consultarInteresseAtivoUseCase;
        this.sincronizarUseCase = sincronizarUseCase;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar oportunidades disponiveis para a credora autenticada")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Oportunidades retornadas"),
        @ApiResponse(
                responseCode = "404",
                description = "Usuario nao possui credora",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<List<OportunidadeResponse>> listar(@AuthenticationPrincipal UsuarioAutenticado principal) {
        List<OportunidadeResponse> resposta = listarUseCase.executar(principal.id()).stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Detalhe de uma oportunidade")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Oportunidade retornada"),
        @ApiResponse(
                responseCode = "404",
                description = "Credora ou oportunidade nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<OportunidadeResponse> consultar(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        return ResponseEntity.ok(mapper.toResponse(consultarUseCase.executar(principal.id(), id)));
    }

    @PostMapping("/{id}/interesses")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Registrar interesse da credora autenticada na oportunidade")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Interesse registrado"),
        @ApiResponse(
                responseCode = "404",
                description = "Credora ou oportunidade nao encontrada",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Interesse ativo ja existe",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "422",
                description = "Credora nao elegivel ou oportunidade indisponivel",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<InteresseResponse> registrarInteresse(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        InteresseResponse resposta = mapper.toResponse(registrarInteresseUseCase.executar(principal.id(), id));
        return ResponseEntity.created(URI.create("/api/v1/credores/oportunidades/" + id + "/interesses/me"))
                .body(resposta);
    }

    @DeleteMapping("/{id}/interesses/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cancelar o interesse proprio da credora na oportunidade")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Interesse cancelado"),
        @ApiResponse(
                responseCode = "404",
                description = "Credora ou interesse ativo nao encontrado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> cancelarInteresse(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        cancelarInteresseUseCase.executar(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/interesses/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar o interesse ativo da credora autenticada na oportunidade")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Interesse ativo retornado"),
        @ApiResponse(
                responseCode = "401",
                description = "Token ausente",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Credora ou interesse ativo nao encontrado",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<InteresseResponse> consultarInteresseAtivo(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        return ResponseEntity.ok(mapper.toResponse(consultarInteresseAtivoUseCase.executar(principal.id(), id)));
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Sincronizar oportunidades a partir das propostas elegiveis (admin)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sincronizacao concluida"),
        @ApiResponse(
                responseCode = "403",
                description = "Nao e ADMIN",
                content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<SincronizacaoOportunidadesResponse> sincronizar() {
        return ResponseEntity.ok(new SincronizacaoOportunidadesResponse(sincronizarUseCase.executar()));
    }
}
