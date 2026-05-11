package com.dynamis.sep_api.identity.infrastructure.persistence;

import com.dynamis.sep_api.identity.domain.model.RefreshToken;
import com.dynamis.sep_api.identity.domain.model.RefreshTokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByFamilyId(UUID familyId);

    List<RefreshToken> findByUsuarioIdAndStatus(UUID usuarioId, RefreshTokenStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.status = 'REVOGADO', r.revogadoEm = :agora "
            + "WHERE r.familyId = :familyId AND r.status <> 'REVOGADO'")
    int revogarFamilia(@Param("familyId") UUID familyId, @Param("agora") OffsetDateTime agora);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.status = 'REVOGADO', r.revogadoEm = :agora "
            + "WHERE r.usuarioId = :usuarioId AND r.status <> 'REVOGADO'")
    int revogarTodosDoUsuario(@Param("usuarioId") UUID usuarioId, @Param("agora") OffsetDateTime agora);
}
