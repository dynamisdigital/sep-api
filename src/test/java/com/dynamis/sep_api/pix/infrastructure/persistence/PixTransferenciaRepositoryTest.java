package com.dynamis.sep_api.pix.infrastructure.persistence;

import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class PixTransferenciaRepositoryTest {

    private static final List<StatusPixTransferencia> OCUPADOS = List.of(
            StatusPixTransferencia.CRIADA,
            StatusPixTransferencia.SOLICITADA,
            StatusPixTransferencia.PROCESSANDO,
            StatusPixTransferencia.CONCLUIDA);

    @Autowired
    private PixTransferenciaRepository repository;

    private PixTransferencia desembolso(UUID contratoId, String idempotencyKey) {
        return PixTransferencia.criarDesembolso(
                contratoId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10000.00"),
                "a".repeat(64),
                "us****om",
                idempotencyKey,
                "corr-1");
    }

    @Test
    void persisteEBuscaPorIdempotencyKey() {
        UUID contratoId = UUID.randomUUID();
        repository.saveAndFlush(desembolso(contratoId, "idem-1"));

        assertThat(repository.findByIdempotencyKey("idem-1")).isPresent();
        assertThat(repository.findByIdempotencyKey("inexistente")).isEmpty();
    }

    @Test
    void buscaTransferenciaQueOcupaContrato() {
        UUID contratoId = UUID.randomUUID();
        repository.saveAndFlush(desembolso(contratoId, "idem-1"));

        assertThat(repository.findFirstByContratoIdAndStatusInOrderByDataCriacaoDesc(contratoId, OCUPADOS))
                .isPresent();
    }

    @Test
    void uniqueParcial_bloqueiaSegundoDesembolsoAtivoNoMesmoContrato() {
        UUID contratoId = UUID.randomUUID();
        repository.saveAndFlush(desembolso(contratoId, "idem-1"));

        assertThatThrownBy(() -> repository.saveAndFlush(desembolso(contratoId, "idem-2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueParcial_liberaContratoAposFalha() {
        UUID contratoId = UUID.randomUUID();
        PixTransferencia primeira = desembolso(contratoId, "idem-1");
        primeira.marcarFalhou();
        repository.saveAndFlush(primeira);

        // FALHOU nao ocupa o contrato: novo desembolso ativo eh permitido.
        PixTransferencia segunda = repository.saveAndFlush(desembolso(contratoId, "idem-2"));

        assertThat(segunda.getStatus()).isEqualTo(StatusPixTransferencia.CRIADA);
    }
}
