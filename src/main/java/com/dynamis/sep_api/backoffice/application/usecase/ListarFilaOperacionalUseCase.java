package com.dynamis.sep_api.backoffice.application.usecase;

import com.dynamis.sep_api.backoffice.application.dto.FiltrosFilaOperacional;
import com.dynamis.sep_api.backoffice.application.dto.ItemFilaSummary;
import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ItemFilaOperacionalRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista a fila operacional (Sprint 14 Task 14.3) com filtros opcionais combinaveis e paginacao
 * via Spring Data {@link Specification}.
 *
 * <p>Defesas (fix review manual Task 14.3):
 *
 * <ul>
 *   <li>Page size eh limitada a {@link #PAGE_SIZE_MAX} = 100. Chamadas com {@code Pageable.unpaged()}
 *       ou size maior sao normalizadas pra 100 itens.
 *   <li>Quando o caller nao define {@code Sort}, aplica ordenacao default por peso de prioridade
 *       descendente (CRITICA -&gt; ALTA -&gt; MEDIA -&gt; BAIXA) e {@code dataAbertura} ascendente
 *       via {@code CASE} no JPQL — evita ordem lexicografica errada do enum em VARCHAR.
 * </ul>
 */
@Service
public class ListarFilaOperacionalUseCase {

    public static final int PAGE_SIZE_MAX = 100;

    private final ItemFilaOperacionalRepository repository;

    public ListarFilaOperacionalUseCase(ItemFilaOperacionalRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<ItemFilaSummary> listar(FiltrosFilaOperacional filtros, Pageable pageable) {
        Pageable normalizado = normalizar(pageable);
        Specification<ItemFilaOperacional> spec = montar(filtros, normalizado);
        return repository.findAll(spec, normalizado).map(ItemFilaSummary::de);
    }

    private static Pageable normalizar(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, PAGE_SIZE_MAX);
        }
        if (pageable.getPageSize() > PAGE_SIZE_MAX) {
            return PageRequest.of(pageable.getPageNumber(), PAGE_SIZE_MAX, pageable.getSort());
        }
        return pageable;
    }

    private static Specification<ItemFilaOperacional> montar(FiltrosFilaOperacional filtros, Pageable pageable) {
        FiltrosFilaOperacional f = filtros == null ? FiltrosFilaOperacional.vazio() : filtros;
        boolean aplicarSortDefault = pageable.getSort().isUnsorted();
        return (root, query, cb) -> {
            if (aplicarSortDefault && query.getResultType() == ItemFilaOperacional.class) {
                query.orderBy(sortDefault(root, cb));
            }
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

    private static List<Order> sortDefault(Root<ItemFilaOperacional> root, CriteriaBuilder cb) {
        Expression<Integer> peso = cb.<Integer>selectCase()
                .when(cb.equal(root.get("prioridade"), PrioridadeItem.CRITICA), PrioridadeItem.CRITICA.ordinalPeso())
                .when(cb.equal(root.get("prioridade"), PrioridadeItem.ALTA), PrioridadeItem.ALTA.ordinalPeso())
                .when(cb.equal(root.get("prioridade"), PrioridadeItem.MEDIA), PrioridadeItem.MEDIA.ordinalPeso())
                .otherwise(PrioridadeItem.BAIXA.ordinalPeso());
        return List.of(cb.desc(peso), cb.asc(root.get("dataAbertura")));
    }
}
