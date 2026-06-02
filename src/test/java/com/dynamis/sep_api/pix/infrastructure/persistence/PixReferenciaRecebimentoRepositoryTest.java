package com.dynamis.sep_api.pix.infrastructure.persistence;

import com.dynamis.sep_api.pix.domain.model.PixReferenciaRecebimento;
import com.dynamis.sep_api.pix.domain.vo.StatusPixReferenciaRecebimento;
import com.dynamis.sep_api.shared.audit.AuditorAwareImpl;
import com.dynamis.sep_api.shared.audit.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class PixReferenciaRecebimentoRepositoryTest {

    @Autowired
    private PixReferenciaRecebimentoRepository repository;

    private PixReferenciaRecebimento referencia(UUID parcelaId, String txid) {
        return PixReferenciaRecebimento.criar(
                parcelaId, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("500.00"), txid, "corr-1");
    }

    @Test
    void persisteEBuscaPorTxid() {
        repository.saveAndFlush(referencia(UUID.randomUUID(), "txid-1"));

        assertThat(repository.findByTxid("txid-1")).isPresent();
        assertThat(repository.findByTxid("inexistente")).isEmpty();
    }

    @Test
    void txid_unico() {
        repository.saveAndFlush(referencia(UUID.randomUUID(), "txid-dup"));

        assertThatThrownBy(() -> repository.saveAndFlush(referencia(UUID.randomUUID(), "txid-dup")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueParcial_umaReferenciaAtivaPorParcela() {
        UUID parcelaId = UUID.randomUUID();
        repository.saveAndFlush(referencia(parcelaId, "txid-a"));

        assertThatThrownBy(() -> repository.saveAndFlush(referencia(parcelaId, "txid-b")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueParcial_liberaParcelaQuandoReferenciaNaoAtiva() {
        UUID parcelaId = UUID.randomUUID();
        PixReferenciaRecebimento paga = referencia(parcelaId, "txid-a");
        paga.marcarPaga();
        repository.saveAndFlush(paga);

        // PAGA nao ocupa o slot ATIVA: nova referencia ATIVA da mesma parcela eh permitida.
        PixReferenciaRecebimento nova = repository.saveAndFlush(referencia(parcelaId, "txid-b"));

        assertThat(nova.getStatus()).isEqualTo(StatusPixReferenciaRecebimento.ATIVA);
        assertThat(repository.findByParcelaIdAndStatus(parcelaId, StatusPixReferenciaRecebimento.ATIVA))
                .isPresent();
    }
}
