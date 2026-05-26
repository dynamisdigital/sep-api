package com.dynamis.sep_api.backoffice.application.service;

import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Criacao idempotente de itens da fila operacional (Sprint 14 Task 14.2).
 *
 * <p>Garante que listeners e job consolidador nao criem itens duplicados pra mesma entidade do
 * mesmo tipo enquanto houver item ativo. Defesa em profundidade: check + catch de
 * {@link DataIntegrityViolationException} (UNIQUE parcial de {@code item_fila_operacional}) em
 * caso de race entre verificacao e insert.
 *
 * <p>Sem {@code @Transactional} aqui: a tx eh aberta em {@link ItemFilaOperacionalInserter#inserir}
 * com {@code REQUIRES_NEW}. Isso garante que o catch de {@link DataIntegrityViolationException}
 * fica fora da tx do insert — evita {@code UnexpectedRollbackException} no commit final (fix
 * review manual Task 14.2).
 */
@Service
public class CriarItemFilaOperacionalService {

    private static final Logger LOG = LoggerFactory.getLogger(CriarItemFilaOperacionalService.class);

    private static final Set<StatusItemFila> ATIVOS = Set.of(StatusItemFila.ABERTO, StatusItemFila.EM_TRATAMENTO);

    private final ItemFilaOperacionalRepository repository;
    private final ItemFilaOperacionalInserter inserter;

    public CriarItemFilaOperacionalService(
            ItemFilaOperacionalRepository repository, ItemFilaOperacionalInserter inserter) {
        this.repository = repository;
        this.inserter = inserter;
    }

    public record CriarItemCommand(
            TipoItemFila tipo,
            PrioridadeItem prioridade,
            TipoEntidadeReferenciada tipoEntidade,
            UUID entidadeId,
            String titulo,
            String descricao) {}

    public Optional<UUID> criarSeAusente(CriarItemCommand cmd) {
        if (repository.existsByTipoAndTipoEntidadeAndEntidadeIdAndStatusIn(
                cmd.tipo(), cmd.tipoEntidade(), cmd.entidadeId(), ATIVOS)) {
            return Optional.empty();
        }

        try {
            return Optional.of(inserter.inserir(cmd));
        } catch (DataIntegrityViolationException race) {
            LOG.info(
                    "race idempotencia detectada para tipo={} entidade={}; item duplicado nao criado",
                    cmd.tipo(),
                    cmd.entidadeId());
            return Optional.empty();
        }
    }
}
