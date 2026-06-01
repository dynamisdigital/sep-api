package com.dynamis.sep_api.pix.infrastructure.adapter.escrow;

import com.dynamis.sep_api.escrow.domain.model.ContaEscrow;
import com.dynamis.sep_api.escrow.domain.vo.StatusContaEscrow;
import com.dynamis.sep_api.escrow.infrastructure.persistence.WalletRepository;
import com.dynamis.sep_api.pix.application.port.out.EscrowDesembolsoQueryPort;
import com.dynamis.sep_api.pix.application.port.out.dto.EscrowDesembolsoView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter que traduz {@link EscrowDesembolsoQueryPort} para o repositorio de {@code escrow}. A conta
 * escrow eh {@code LAZY} na wallet, por isso a leitura roda em transacao read-only.
 *
 * <p>O {@code walletExternalId} vem da conta escrow e pode ser {@code null} para contas locais
 * (Sprint 12). A operacao eh considerada operacional quando a conta esta {@link
 * StatusContaEscrow#ATIVA}; o desembolso via Celcoin real depende do external id (gap documentado).
 */
@Component
public class EscrowDesembolsoQueryAdapter implements EscrowDesembolsoQueryPort {

    private final WalletRepository walletRepository;

    public EscrowDesembolsoQueryAdapter(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EscrowDesembolsoView> buscarPorProposta(UUID propostaId) {
        return walletRepository.findByPropostaId(propostaId).map(wallet -> {
            ContaEscrow conta = wallet.getContaEscrow();
            boolean operacional = conta != null && conta.getStatus() == StatusContaEscrow.ATIVA;
            String walletExternalId = conta != null ? conta.getExternalId() : null;
            return new EscrowDesembolsoView(propostaId, wallet.getId(), walletExternalId, operacional);
        });
    }
}
