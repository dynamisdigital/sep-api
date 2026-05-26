package com.dynamis.sep_api.backoffice.infrastructure.persistence;

import com.dynamis.sep_api.backoffice.domain.model.Reprocesso;
import com.dynamis.sep_api.shared.audit.AuditorAwareImpl;
import com.dynamis.sep_api.shared.audit.JpaAuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class ReprocessoRepositoryTest {

    @Autowired
    private ReprocessoRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void contaApenasReprocessosNaJanela() {
        UUID webhook = UUID.randomUUID();
        OffsetDateTime t0 = OffsetDateTime.now().minusHours(36);
        OffsetDateTime t1 = OffsetDateTime.now().minusHours(12);
        OffsetDateTime t2 = OffsetDateTime.now().minusHours(1);

        repository.saveAndFlush(Reprocesso.paraWebhook(null, webhook, t0, UUID.randomUUID()));
        repository.saveAndFlush(Reprocesso.paraWebhook(null, webhook, t1, UUID.randomUUID()));
        repository.saveAndFlush(Reprocesso.paraWebhook(null, webhook, t2, UUID.randomUUID()));

        OffsetDateTime corte24h = OffsetDateTime.now().minusHours(24);

        long dentroJanela = repository.countByIdentificadorExternoAndDataDisparoAfter(webhook.toString(), corte24h);

        assertThat(dentroJanela).isEqualTo(2);
    }

    @Test
    void contaIsolaPorIdentificador() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now();

        repository.saveAndFlush(Reprocesso.paraWebhook(null, a, agora.minusMinutes(10), UUID.randomUUID()));
        repository.saveAndFlush(Reprocesso.paraWebhook(null, a, agora.minusMinutes(5), UUID.randomUUID()));
        repository.saveAndFlush(Reprocesso.paraWebhook(null, b, agora.minusMinutes(2), UUID.randomUUID()));

        OffsetDateTime corte = agora.minusHours(1);

        assertThat(repository.countByIdentificadorExternoAndDataDisparoAfter(a.toString(), corte)).isEqualTo(2);
        assertThat(repository.countByIdentificadorExternoAndDataDisparoAfter(b.toString(), corte)).isEqualTo(1);
    }
}
