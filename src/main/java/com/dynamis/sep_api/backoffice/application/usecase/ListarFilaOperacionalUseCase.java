package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.application.dto.FiltrosFilaOperacional;
import com.dynamis.sep_api.backoffice.application.dto.ItemFilaSummary;
import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista a fila operacional (Sprint 14 Task 14.3) com filtros opcionais combinaveis e paginacao
 * via Spring Data {@link Specification}. Ordenacao default eh definida no controller pela camada
 * web (Task 14.7); aqui o use case respeita o {@link Pageable} recebido.
 */
@Service
public class ListarFilaOperacionalUseCase {

    private final ItemFilaOperacionalRepository repository;

    public ListarFilaOperacionalUseCase(ItemFilaOperacionalRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<ItemFilaSummary> listar(FiltrosFilaOperacional filtros, Pageable pageable) {
        return repository.findAll(montar(filtros), pageable).map(ItemFilaSummary::de);
    }

    private static Specification<ItemFilaOperacional> montar(FiltrosFilaOperacional filtros) {
        FiltrosFilaOperacional f = filtros == null ? FiltrosFilaOperacional.vazio() : filtros;
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (f.tipo() != null) {
                predicados.add(cb.equal(root.get("tipo"), f.tipo()));
            }
            if (f.prioridade() != null) {
                predicados.add(cb.equal(root.get("prioridade"), f.prioridade()));
            }
            if (f.status() != null) {
                predicados.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.dataAberturaDe() != null) {
                predicados.add(cb.greaterThanOrEqualTo(root.get("dataAbertura"), f.dataAberturaDe()));
            }
            if (f.dataAberturaAte() != null) {
                predicados.add(cb.lessThanOrEqualTo(root.get("dataAbertura"), f.dataAberturaAte()));
            }
            if (f.atribuidoA() != null) {
                predicados.add(cb.equal(root.get("atribuidoA"), f.atribuidoA()));
            }
            return predicados.isEmpty() ? cb.conjunction() : cb.and(predicados.toArray(new Predicate[0]));
        };
    }
}
