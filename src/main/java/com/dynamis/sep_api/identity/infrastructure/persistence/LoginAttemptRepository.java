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

    /**
     * Instantes das falhas de um username, mais recentes primeiro. Alimenta a decisao de lockout
     * ({@code PoliticaLockout}), que precisa saber <b>quando</b> as falhas ocorreram — contar nao
     * basta para decidir "N falhas dentro de uma janela" (Sprint 33).
     *
     * <p>O {@code Pageable} e limite defensivo, nao paginacao: evita ler uma cauda longa de falhas
     * sob ataque. O tamanho da pagina <b>deve</b> vir de {@code PoliticaLockout#limiteDeLeitura()},
     * que e derivado da politica de forma a tornar impossivel que o truncamento esconda um bloqueio
     * — qualquer outro valor invalida a decisao (Sprint 34; ate a Sprint 33 o teto era uma constante
     * justificada por premissa sobre o que era gravado).
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

    List<LoginAttempt> findByUsuarioId(UUID usuarioId);
}
