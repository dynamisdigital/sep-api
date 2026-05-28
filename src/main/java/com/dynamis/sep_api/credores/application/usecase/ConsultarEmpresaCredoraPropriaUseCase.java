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

/** Consulta a empresa credora do proprio usuario autenticado (Sprint 16, Task 3). */
@Service
public class ConsultarEmpresaCredoraPropriaUseCase {

    private final EmpresaCredoraRepository empresaRepository;
    private final PerfilCredoraRepository perfilRepository;

    public ConsultarEmpresaCredoraPropriaUseCase(
            EmpresaCredoraRepository empresaRepository, PerfilCredoraRepository perfilRepository) {
        this.empresaRepository = empresaRepository;
        this.perfilRepository = perfilRepository;
    }

    @Transactional(readOnly = true)
    public EmpresaCredoraView executar(UUID usuarioId) {
        EmpresaCredora empresa = empresaRepository
                .findByUsuarioId(usuarioId)
                .orElseThrow(() -> EmpresaCredoraNaoEncontradaException.porUsuario(usuarioId));
        PerfilCredora perfil = perfilRepository
                .findByEmpresaCredoraId(empresa.getId())
                .orElseThrow(() -> new IllegalStateException("Perfil ausente para credora " + empresa.getId()));
        return EmpresaCredoraView.de(empresa, perfil);
    }
}
