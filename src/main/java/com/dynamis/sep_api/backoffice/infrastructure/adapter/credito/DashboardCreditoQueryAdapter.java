package com.dynamis.sep_api.backoffice.infrastructure.adapter.credito;

import com.dynamis.sep_api.backoffice.application.dto.ContadorPorStatusProposta;
import com.dynamis.sep_api.backoffice.application.port.out.DashboardCreditoQueryPort;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** Adapter de fronteira — contagens de propostas por status (Sprint 14 Task 14.5). */
@Component
public class DashboardCreditoQueryAdapter implements DashboardCreditoQueryPort {

    private final PropostaCreditoRepository repository;

    public DashboardCreditoQueryAdapter(PropostaCreditoRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ContadorPorStatusProposta> contagemPorStatus() {
        return repository.contarPorStatus().stream()
                .map(v -> new ContadorPorStatusProposta(v.getStatus().name(), v.getTotal()))
                .toList();
    }
}
