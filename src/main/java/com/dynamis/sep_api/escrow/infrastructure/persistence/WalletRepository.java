package com.dynamis.sep_api.escrow.infrastructure.persistence;

import com.dynamis.sep_api.escrow.domain.model.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByPropostaId(UUID propostaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.propostaId = :propostaId")
    Optional<Wallet> findByPropostaIdForUpdate(UUID propostaId);
}
