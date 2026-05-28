package com.dynamis.sep_api.governanca.application.usecase;

import com.dynamis.sep_api.governanca.application.dto.ParametroOperacionalView;
import com.dynamis.sep_api.governanca.infrastructure.persistence.ParametroOperacionalRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lista todos os parametros operacionais governados, ordenados por chave (Sprint 18). */
@Service
public class ListarParametrosOperacionaisUseCase {

    private final ParametroOperacionalRepository repository;

    public ListarParametrosOperacionaisUseCase(ParametroOperacionalRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ParametroOperacionalView> executar() {
        return repository.findAll(Sort.by("chave")).stream()
                .map(ParametroOperacionalView::de)
                .toList();
    }
}
