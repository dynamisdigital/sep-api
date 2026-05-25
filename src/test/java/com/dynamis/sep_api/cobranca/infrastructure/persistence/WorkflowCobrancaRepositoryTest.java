package com.dynamis.sep_api.cobranca.infrastructure.persistence;

import com.dynamis.sep_api.cobranca.domain.model.WorkflowCobranca;
import com.dynamis.sep_api.shared.audit.AuditorAwareImpl;
import com.dynamis.sep_api.shared.audit.JpaAuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
// Fix review manual Task 13.4: WorkflowCobrancaSeeder eh ApplicationRunner ativo no perfil dev e
// commita rows em sep_dev no boot de @SpringBootTest da suite. @DataJpaTest com rollback nao
// remove esse estado entre execucoes — sobrescreve via property pra garantir banco limpo.
@org.springframework.test.context.TestPropertySource(properties = "app.cobranca.workflow-seed-habilitado=false")
class WorkflowCobrancaRepositoryTest {

    @Autowired
    private WorkflowCobrancaRepository repo;

    @BeforeEach
    void setup() {
        repo.deleteAll();
    }

    @Test
    void persisteEListaEtapasAtivasOrdenadasPorDia() {
        repo.saveAndFlush(WorkflowCobranca.criar("default", 30, List.of("email-firme"), true, false, false));
        repo.saveAndFlush(WorkflowCobranca.criar("default", 0, List.of("email-amigavel"), false, false, false));
        repo.saveAndFlush(WorkflowCobranca.criar("default", 90, List.of(), false, false, true));

        List<WorkflowCobranca> etapas = repo.findByNomeAndAtivoTrueOrderByDiaAtrasoAsc("default");

        assertThat(etapas).hasSize(3);
        assertThat(etapas.get(0).getDiaAtraso()).isZero();
        assertThat(etapas.get(2).isMarcarInadimplente()).isTrue();
    }

    @Test
    void uniqueParcial_naoPermiteDuasEtapasAtivasMesmoDia() {
        repo.saveAndFlush(WorkflowCobranca.criar("default", 30, List.of("a"), false, false, false));

        WorkflowCobranca duplicada = WorkflowCobranca.criar("default", 30, List.of("b"), false, false, false);

        assertThatThrownBy(() -> repo.saveAndFlush(duplicada)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueParcial_permiteNovaEtapaAposDesativarAnterior() {
        WorkflowCobranca antiga = WorkflowCobranca.criar("default", 30, List.of("a"), false, false, false);
        antiga = repo.saveAndFlush(antiga);
        antiga.desativar();
        repo.saveAndFlush(antiga);

        WorkflowCobranca nova =
                repo.saveAndFlush(WorkflowCobranca.criar("default", 30, List.of("b"), false, false, false));

        assertThat(repo.findByNomeAndDiaAtrasoAndAtivoTrue("default", 30))
                .map(WorkflowCobranca::getId)
                .contains(nova.getId());
    }
}
