package com.dynamis.sep_api.backoffice.infrastructure.persistence;

import com.dynamis.sep_api.backoffice.domain.model.ItemFilaOperacional;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
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

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class ItemFilaOperacionalRepositoryTest {

    private static final Set<StatusItemFila> ATIVOS = Set.of(StatusItemFila.ABERTO, StatusItemFila.EM_TRATAMENTO);

    @Autowired
    private ItemFilaOperacionalRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void existsByTipoEntidade_consideraApenasAtivos() {
        UUID entidade = UUID.randomUUID();
        ItemFilaOperacional item = repository.saveAndFlush(novoItem(entidade));

        assertThat(repository.existsByTipoAndTipoEntidadeAndEntidadeIdAndStatusIn(
                        item.getTipo(), item.getTipoEntidade(), entidade, ATIVOS))
                .isTrue();

        item.assumir(UUID.randomUUID());
        item.resolver(OffsetDateTime.now());
        repository.saveAndFlush(item);

        assertThat(repository.existsByTipoAndTipoEntidadeAndEntidadeIdAndStatusIn(
                        item.getTipo(), item.getTipoEntidade(), entidade, ATIVOS))
                .isFalse();
    }

    @Test
    void uniqueParcial_impedeDuplicacaoAtiva() {
        UUID entidade = UUID.randomUUID();
        repository.saveAndFlush(novoItem(entidade));

        assertThatThrownBy(() -> repository.saveAndFlush(novoItem(entidade)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueParcial_aceitaReaberturaAposResolver() {
        UUID entidade = UUID.randomUUID();
        ItemFilaOperacional primeiro = repository.saveAndFlush(novoItem(entidade));
        primeiro.assumir(UUID.randomUUID());
        primeiro.resolver(OffsetDateTime.now());
        repository.saveAndFlush(primeiro);

        ItemFilaOperacional segundo = repository.saveAndFlush(novoItem(entidade));

        assertThat(segundo.getId()).isNotEqualTo(primeiro.getId());
    }

    private ItemFilaOperacional novoItem(UUID entidade) {
        return ItemFilaOperacional.abrir(
                TipoItemFila.ONBOARDING_ERRO,
                PrioridadeItem.ALTA,
                TipoEntidadeReferenciada.ONBOARDING,
                entidade,
                "Onboarding REPROVADO",
                null,
                OffsetDateTime.now());
    }
}
