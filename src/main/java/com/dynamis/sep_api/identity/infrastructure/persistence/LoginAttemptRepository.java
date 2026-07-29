package com.dynamis.sep_api.identity.infrastructure.persistence;

import com.dynamis.sep_api.identity.domain.model.LoginAttempt;
import com.dynamis.sep_api.identity.domain.model.LoginAttemptStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    @Query("SELECT COUNT(l) FROM LoginAttempt l "
            + "WHERE l.username = :username "
            + "AND l.status IN :statuses "
            + "AND l.dataTentativa >= :inicioJanela")
    long countByUsernameAndStatusInAndJanela(
            @Param("username") String username,
            @Param("statuses") List<LoginAttemptStatus> statuses,
            @Param("inicioJanela") OffsetDateTime inicioJanela);

    /**
     * Instantes das falhas de um username, mais recentes primeiro. Alimenta a decisao de lockout
     * ({@code PoliticaLockout}), que precisa saber <b>quando</b> as falhas ocorreram — contar nao
     * basta para decidir "N falhas dentro de uma janela" (Sprint 33).
     *
     * <p>O {@code Pageable} e limite defensivo, nao paginacao: evita ler uma cauda longa de falhas
     * sob ataque. E seguro porque qualquer janela que bloqueie estara entre as falhas mais recentes.
     */
    @Query("SELECT l.dataTentativa FROM LoginAttempt l "
            + "WHERE l.username = :username "
            + "AND l.status IN :statuses "
            + "AND l.dataTentativa >= :inicioJanela "
            + "ORDER BY l.dataTentativa DESC")
    List<OffsetDateTime> buscarInstantesDeFalha(
            @Param("username") String username,
            @Param("statuses") List<LoginAttemptStatus> statuses,
            @Param("inicioJanela") OffsetDateTime inicioJanela,
            Pageable limite);

    @Query("SELECT COUNT(l) FROM LoginAttempt l " + "WHERE l.ip = :ip " + "AND l.dataTentativa >= :inicioJanela")
    long countByIpAndJanela(@Param("ip") String ip, @Param("inicioJanela") OffsetDateTime inicioJanela);

    List<LoginAttempt> findByUsuarioId(UUID usuarioId);
}
