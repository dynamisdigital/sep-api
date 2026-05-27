package com.dynamis.sep_api.backoffice.web.controller;

import com.dynamis.sep_api.backoffice.application.dto.FiltrosFilaOperacional;
import com.dynamis.sep_api.backoffice.application.usecase.AssumirItemFilaUseCase;
import com.dynamis.sep_api.backoffice.application.usecase.ConsultarItemFilaUseCase;
import com.dynamis.sep_api.backoffice.application.usecase.ListarFilaOperacionalUseCase;
import com.dynamis.sep_api.backoffice.application.usecase.MarcarItemIgnoradoUseCase;
import com.dynamis.sep_api.backoffice.application.usecase.MarcarItemResolvidoUseCase;
import com.dynamis.sep_api.backoffice.application.usecase.RegistrarComentarioUseCase;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.backoffice.web.dto.ComentarioInternoResponse;
import com.dynamis.sep_api.backoffice.web.dto.ComentarioRequest;
import com.dynamis.sep_api.backoffice.web.dto.IgnorarRequest;
import com.dynamis.sep_api.backoffice.web.dto.ItemFilaDetalheResponse;
import com.dynamis.sep_api.backoffice.web.dto.ItemFilaResponse;
import com.dynamis.sep_api.backoffice.web.dto.ResolverRequest;
import com.dynamis.sep_api.identity.infrastructure.security.RequireStepUp;
import com.dynamis.sep_api.identity.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Endpoints da fila operacional do backoffice (Sprint 14 Task 14.7).
 *
 * <ul>
 *   <li>GET /api/v1/backoffice/fila — listagem com filtros + paginacao
 *   <li>GET /api/v1/backoffice/fila/{id} — detalhe + comentarios + objeto original
 *   <li>POST /api/v1/backoffice/fila/{id}/assumir — operador assume
 *   <li>POST /api/v1/backoffice/fila/{id}/comentarios — registra comentario
 *   <li>PATCH /api/v1/backoffice/fila/{id}/resolver — step-up + justificativa
 *   <li>PATCH /api/v1/backoffice/fila/{id}/ignorar — step-up + justificativa
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/backoffice/fila")
@Tag(name = "Backoffice — Fila Operacional")
@PreAuthorize("hasAnyRole('FINANCEIRO','BACKOFFICE','ADMIN')")
public class BackofficeController {

    private final ListarFilaOperacionalUseCase listar;
    private final ConsultarItemFilaUseCase consultar;
    private final AssumirItemFilaUseCase assumir;
    private final RegistrarComentarioUseCase registrarComentario;
    private final MarcarItemResolvidoUseCase resolver;
    private final MarcarItemIgnoradoUseCase ignorar;

    public BackofficeController(
            ListarFilaOperacionalUseCase listar,
            ConsultarItemFilaUseCase consultar,
            AssumirItemFilaUseCase assumir,
            RegistrarComentarioUseCase registrarComentario,
            MarcarItemResolvidoUseCase resolver,
            MarcarItemIgnoradoUseCase ignorar) {
        this.listar = listar;
        this.consultar = consultar;
        this.assumir = assumir;
        this.registrarComentario = registrarComentario;
        this.resolver = resolver;
        this.ignorar = ignorar;
    }

    @GetMapping
    @Operation(
            summary = "Lista itens da fila operacional",
            description = "Filtros opcionais combinaveis + paginacao. Page size limitada a 100.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listagem retornada."),
        @ApiResponse(responseCode = "401", description = "Sem autenticacao."),
        @ApiResponse(responseCode = "403", description = "Sem role FINANCEIRO/BACKOFFICE/ADMIN.")
    })
    public ResponseEntity<Page<ItemFilaResponse>> listar(
            @RequestParam(required = false) TipoItemFila tipo,
            @RequestParam(required = false) PrioridadeItem prioridade,
            @RequestParam(required = false) StatusItemFila status,
            @RequestParam(name = "data_abertura_de", required = false) OffsetDateTime dataAberturaDe,
            @RequestParam(name = "data_abertura_ate", required = false) OffsetDateTime dataAberturaAte,
            @RequestParam(name = "atribuido_a", required = false) UUID atribuidoA,
            Pageable pageable) {
        FiltrosFilaOperacional filtros =
                new FiltrosFilaOperacional(tipo, prioridade, status, dataAberturaDe, dataAberturaAte, atribuidoA);
        return ResponseEntity.ok(listar.listar(filtros, sanitizarSort(pageable)).map(ItemFilaResponse::from));
    }

    /**
     * Remove sort por {@code prioridade} (fix code review Task 14.7) — coluna eh VARCHAR e ordem
     * lexicografica devolve ordem errada (MEDIA &gt; CRITICA &gt; BAIXA &gt; ALTA). Caller pode
     * passar outros sorts; quando vazio, use case aplica CASE-based default por peso.
     */
    private static Pageable sanitizarSort(Pageable pageable) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return pageable;
        }
        Sort sortLimpo = Sort.by(pageable.getSort().stream()
                .filter(o -> !"prioridade".equalsIgnoreCase(o.getProperty()))
                .toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortLimpo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha item com comentarios e objeto original")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Detalhe retornado."),
        @ApiResponse(responseCode = "401", description = "Sem autenticacao."),
        @ApiResponse(responseCode = "403", description = "Sem role FINANCEIRO/BACKOFFICE/ADMIN."),
        @ApiResponse(responseCode = "404", description = "Item nao encontrado.")
    })
    public ResponseEntity<ItemFilaDetalheResponse> consultar(@PathVariable UUID id) {
        return ResponseEntity.ok(ItemFilaDetalheResponse.from(consultar.consultar(id)));
    }

    @PostMapping("/{id}/assumir")
    @Operation(
            summary = "Operador assume item",
            description = "Transiciona ABERTO -> EM_TRATAMENTO; registra atribuidoA.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Item assumido."),
        @ApiResponse(responseCode = "401", description = "Sem autenticacao."),
        @ApiResponse(responseCode = "403", description = "Sem role FINANCEIRO/BACKOFFICE/ADMIN."),
        @ApiResponse(responseCode = "404", description = "Item nao encontrado."),
        @ApiResponse(responseCode = "409", description = "Transicao invalida (item nao esta ABERTO).")
    })
    public ResponseEntity<ItemFilaResponse> assumir(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioAutenticado principal) {
        var item = assumir.executar(id, principal.id());
        return ResponseEntity.ok(
                ItemFilaResponse.from(com.dynamis.sep_api.backoffice.application.dto.ItemFilaSummary.de(item)));
    }

    @PostMapping("/{id}/comentarios")
    @Operation(
            summary = "Registra comentario interno",
            description = "Permitido em qualquer status. Conteudo 1-10000 chars.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Comentario criado."),
        @ApiResponse(responseCode = "400", description = "Conteudo invalido."),
        @ApiResponse(responseCode = "401", description = "Sem autenticacao."),
        @ApiResponse(responseCode = "403", description = "Sem role FINANCEIRO/BACKOFFICE/ADMIN."),
        @ApiResponse(responseCode = "404", description = "Item nao encontrado.")
    })
    public ResponseEntity<ComentarioInternoResponse> comentar(
            @PathVariable UUID id,
            @Valid @RequestBody ComentarioRequest request,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        var c = registrarComentario.executar(id, principal.id(), request.conteudo());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ComentarioInternoResponse.from(
                        com.dynamis.sep_api.backoffice.application.dto.ComentarioInternoSummary.de(c)));
    }

    @PatchMapping("/{id}/resolver")
    @RequireStepUp
    @Operation(
            summary = "Marca item como RESOLVIDO",
            description =
                    "Exige step-up. Justificativa minima de 20 caracteres; transiciona EM_TRATAMENTO -> RESOLVIDO.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Item resolvido."),
        @ApiResponse(responseCode = "400", description = "Justificativa curta ou invalida."),
        @ApiResponse(responseCode = "401", description = "Sem autenticacao."),
        @ApiResponse(responseCode = "403", description = "Sem role autorizada ou step-up ausente."),
        @ApiResponse(responseCode = "404", description = "Item nao encontrado."),
        @ApiResponse(responseCode = "409", description = "Item nao esta em EM_TRATAMENTO.")
    })
    public ResponseEntity<ItemFilaResponse> resolver(
            @PathVariable UUID id,
            @Valid @RequestBody ResolverRequest request,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        var item = resolver.executar(id, principal.id(), request.justificativa());
        return ResponseEntity.ok(
                ItemFilaResponse.from(com.dynamis.sep_api.backoffice.application.dto.ItemFilaSummary.de(item)));
    }

    @PatchMapping("/{id}/ignorar")
    @RequireStepUp
    @Operation(
            summary = "Marca item como IGNORADO",
            description = "Exige step-up. Justificativa minima de 20 caracteres; aceita ABERTO ou EM_TRATAMENTO.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Item ignorado."),
        @ApiResponse(responseCode = "400", description = "Justificativa curta ou invalida."),
        @ApiResponse(responseCode = "401", description = "Sem autenticacao."),
        @ApiResponse(responseCode = "403", description = "Sem role autorizada ou step-up ausente."),
        @ApiResponse(responseCode = "404", description = "Item nao encontrado."),
        @ApiResponse(responseCode = "409", description = "Item ja esta em status final.")
    })
    public ResponseEntity<ItemFilaResponse> ignorar(
            @PathVariable UUID id,
            @Valid @RequestBody IgnorarRequest request,
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        var item = ignorar.executar(id, principal.id(), request.justificativa());
        return ResponseEntity.ok(
                ItemFilaResponse.from(com.dynamis.sep_api.backoffice.application.dto.ItemFilaSummary.de(item)));
    }
}
