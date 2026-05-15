package com.dynamis.sep_api.onboarding.infrastructure.persistence;

import com.dynamis.sep_api.onboarding.domain.model.ConsultaPld;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.AlvoPld;
import com.dynamis.sep_api.onboarding.domain.vo.BasePld;
import com.dynamis.sep_api.onboarding.domain.vo.SeveridadePld;
import com.dynamis.sep_api.shared.audit.AuditorAwareImpl;
import com.dynamis.sep_api.shared.audit.JpaAuditingConfig;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class ConsultaPldRepositoryTest {

    private static final String CNPJ_VALIDO = "11222333000181";

    @Autowired
    private ConsultaPldRepository pldRepository;

    @Autowired
    private SolicitacaoOnboardingRepository solicitacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID solicitacaoId;

    @BeforeEach
    void setup() {
        pldRepository.deleteAll();
        solicitacaoRepository.deleteAll();
        usuarioRepository.deleteAll();
        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("pld@sep.test", "hash", Role.CLIENTE));
        SolicitacaoOnboarding s = solicitacaoRepository.saveAndFlush(
                SolicitacaoOnboarding.criarEmpresa(u.getId(), CNPJ_VALIDO, "ACME LTDA"));
        solicitacaoId = s.getId();
    }

    @Test
    void consultaPldLimpaPersistePadraoSemHit() {
        ConsultaPld limpa =
                ConsultaPld.limpa(solicitacaoId, AlvoPld.EMPRESA, CNPJ_VALIDO, BasePld.COAF, "{\"hit\":false}");

        ConsultaPld salva = pldRepository.saveAndFlush(limpa);
        ConsultaPld recarregada = pldRepository.findById(salva.getId()).orElseThrow();

        assertThat(recarregada.isHit()).isFalse();
        assertThat(recarregada.getAlvoTipo()).isEqualTo(AlvoPld.EMPRESA);
        assertThat(recarregada.getAlvoDocumento()).isEqualTo(CNPJ_VALIDO);
        assertThat(recarregada.getBase()).isEqualTo(BasePld.COAF);
        assertThat(recarregada.getMotivo()).isNull();
        assertThat(recarregada.getSeveridade()).isNull();
        assertThat(recarregada.getRetencaoAte())
                .isAfterOrEqualTo(LocalDate.now().plusYears(5).minusDays(1));
    }

    @Test
    void consultaPldHitPersisteMotivoSeveridade() {
        ConsultaPld hit = ConsultaPld.hit(
                solicitacaoId,
                AlvoPld.EMPRESA,
                CNPJ_VALIDO,
                BasePld.OFAC,
                "Sancao internacional",
                SeveridadePld.ALTA,
                LocalDate.of(2024, 1, 1),
                "{\"hit\":true}");

        ConsultaPld salva = pldRepository.saveAndFlush(hit);
        ConsultaPld recarregada = pldRepository.findById(salva.getId()).orElseThrow();

        assertThat(recarregada.isHit()).isTrue();
        assertThat(recarregada.getMotivo()).isEqualTo("Sancao internacional");
        assertThat(recarregada.getSeveridade()).isEqualTo(SeveridadePld.ALTA);
        assertThat(recarregada.getBase()).isEqualTo(BasePld.OFAC);
    }

    @Test
    void findBySolicitacaoIdRetornaTodasAsConsultas() {
        pldRepository.saveAndFlush(ConsultaPld.limpa(solicitacaoId, AlvoPld.EMPRESA, CNPJ_VALIDO, BasePld.COAF, "{}"));
        pldRepository.saveAndFlush(ConsultaPld.limpa(solicitacaoId, AlvoPld.EMPRESA, CNPJ_VALIDO, BasePld.OFAC, "{}"));
        pldRepository.saveAndFlush(
                ConsultaPld.limpa(solicitacaoId, AlvoPld.EMPRESA, CNPJ_VALIDO, BasePld.INTERPOL, "{}"));
        pldRepository.saveAndFlush(ConsultaPld.limpa(solicitacaoId, AlvoPld.EMPRESA, CNPJ_VALIDO, BasePld.MTE, "{}"));

        assertThat(pldRepository.findBySolicitacaoId(solicitacaoId)).hasSize(4);
    }

    @Test
    void existsBySolicitacaoIdAndHitTrueDetectaQualquerHit() {
        pldRepository.saveAndFlush(ConsultaPld.limpa(solicitacaoId, AlvoPld.EMPRESA, CNPJ_VALIDO, BasePld.COAF, "{}"));
        pldRepository.saveAndFlush(ConsultaPld.hit(
                solicitacaoId,
                AlvoPld.EMPRESA,
                CNPJ_VALIDO,
                BasePld.OFAC,
                "motivo",
                SeveridadePld.MEDIA,
                LocalDate.now(),
                "{}"));

        assertThat(pldRepository.existsBySolicitacaoIdAndHitTrue(solicitacaoId)).isTrue();
    }
}
