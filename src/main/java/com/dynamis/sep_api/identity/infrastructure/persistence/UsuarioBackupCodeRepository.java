package com.dynamis.sep_api.identity.infrastructure.persistence;

import com.dynamis.sep_api.identity.domain.model.UsuarioBackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UsuarioBackupCodeRepository extends JpaRepository<UsuarioBackupCode, UUID> {

    List<UsuarioBackupCode> findByUsuarioIdAndUsadoFalse(UUID usuarioId);

    List<UsuarioBackupCode> findByUsuarioId(UUID usuarioId);

    long countByUsuarioIdAndUsadoFalse(UUID usuarioId);

    void deleteByUsuarioId(UUID usuarioId);
}
