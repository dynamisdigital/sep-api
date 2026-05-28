package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.EmpresaCredoraView;
import com.dynamis.sep_api.credores.domain.exception.EmpresaCredoraNaoEncontradaException;
import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import com.dynamis.sep_api.credores.domain.model.PerfilCredora;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.PerfilCredoraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Consulta administrativa de qualquer empresa credora pelo id (Sprint 16, Task 3). */
@Service
public class ConsultarEmpresaCredoraPorIdUseCase {

    private final EmpresaCredoraRepository empresaRepository;
    private final PerfilCredoraRepository perfilRepository;

    public ConsultarEmpresaCredoraPorIdUseCase(
            EmpresaCredoraRepository empresaRepository, PerfilCredoraRepository perfilRepository) {
        this.empresaRepository = empresaRepository;
        this.perfilRepository = perfilRepository;
    }

    @Transactional(readOnly = true)
    public EmpresaCredoraView executar(UUID credoraId) {
        EmpresaCredora empresa = empresaRepository
                .findById(credoraId)
                .orElseThrow(() -> new EmpresaCredoraNaoEncontradaException(credoraId));
        PerfilCredora perfil = perfilRepository
                .findByEmpresaCredoraId(empresa.getId())
                .orElseThrow(() -> new IllegalStateException("Perfil ausente para credora " + empresa.getId()));
        return EmpresaCredoraView.de(empresa, perfil);
    }
}
