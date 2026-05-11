package com.dynamis.sep_api.shared.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogSegurancaRepository extends JpaRepository<AuditLogSeguranca, UUID> {

    List<AuditLogSeguranca> findByUsuarioIdOrderByDataEventoDesc(UUID usuarioId);

    List<AuditLogSeguranca> findByTipoOrderByDataEventoDesc(TipoEventoSeguranca tipo);

    List<AuditLogSeguranca> findByUsuarioIdAndTipoOrderByDataEventoDesc(UUID usuarioId, TipoEventoSeguranca tipo);
}
