package com.dynamis.sep_api.backoffice.infrastructure.adapter.onboarding;

import com.dynamis.sep_api.backoffice.application.dto.ObjetoOriginalResumo;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OnboardingObjetoOriginalAdapterTest {

    @Test
    void tipoSuportado_eOnboarding() {
        assertThat(new OnboardingObjetoOriginalAdapter(mock(SolicitacaoOnboardingRepository.class)).tipoSuportado())
                .isEqualTo(TipoEntidadeReferenciada.ONBOARDING);
    }

    @Test
    void buscar_existente_devolveResumo() {
        SolicitacaoOnboardingRepository repo = mock(SolicitacaoOnboardingRepository.class);
        SolicitacaoOnboarding s = SolicitacaoOnboarding.criarPessoa(
                UUID.randomUUID(), new Cpf("52998224725"), "Tomador", LocalDate.of(1990, 1, 1));
        when(repo.findById(s.getId())).thenReturn(Optional.of(s));

        Optional<ObjetoOriginalResumo> resumo = new OnboardingObjetoOriginalAdapter(repo).buscar(s.getId());

        assertThat(resumo).isPresent();
        assertThat(resumo.get().tipoEntidade()).isEqualTo(TipoEntidadeReferenciada.ONBOARDING);
        assertThat(resumo.get().entidadeId()).isEqualTo(s.getId());
        assertThat(resumo.get().status()).isEqualTo(s.getStatus().name());
        assertThat(resumo.get().descricaoCurta()).doesNotContain("52998224725");
    }

    @Test
    void buscar_ausente_devolveEmpty() {
        SolicitacaoOnboardingRepository repo = mock(SolicitacaoOnboardingRepository.class);
        when(repo.findById(any())).thenReturn(Optional.empty());

        assertThat(new OnboardingObjetoOriginalAdapter(repo).buscar(UUID.randomUUID())).isEmpty();
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
