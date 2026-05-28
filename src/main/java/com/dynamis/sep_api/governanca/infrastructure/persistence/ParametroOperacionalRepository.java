package com.dynamis.sep_api.governanca.infrastructure.persistence;

import com.dynamis.sep_api.governanca.domain.model.ParametroOperacional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParametroOperacionalRepository extends JpaRepository<ParametroOperacional, UUID> {

    Optional<ParametroOperacional> findByChave(String chave);

    /**
     * Carrega o parametro com lock pessimista {@code SELECT ... FOR UPDATE} para serializar
     * alteracoes concorrentes na mesma chave (Sprint 18 — fix code review). Evita que dois requests
     * leiam a mesma versao e gerem historico duplicado / "ultimo commit vence".
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ParametroOperacional p where p.chave = :chave")
    Optional<ParametroOperacional> findByChaveParaAtualizacao(@Param("chave") String chave);
}
