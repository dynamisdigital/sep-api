package com.dynamis.sep_api.onboarding.infrastructure.persistence;

import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cnpj;
import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSocietario;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class KybEmpresaRepositoryTest {

    private static final String CNPJ_VALIDO = "11222333000181";

    @Autowired
    private KybEmpresaRepository kybRepository;

    @Autowired
    private SolicitacaoOnboardingRepository solicitacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID solicitacaoId;

    @BeforeEach
    void setup() {
        kybRepository.deleteAll();
        solicitacaoRepository.deleteAll();
        usuarioRepository.deleteAll();
        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("kyb@sep.test", "hash", Role.CLIENTE));
        SolicitacaoOnboarding s = solicitacaoRepository.saveAndFlush(
                SolicitacaoOnboarding.criarEmpresa(u.getId(), CNPJ_VALIDO, "ACME Industria LTDA"));
        solicitacaoId = s.getId();
    }

    @Test
    void persistirERecuperarKybPreservaCampos() {
        KybEmpresa nova = KybEmpresa.criar(
                solicitacaoId,
                new Cnpj(CNPJ_VALIDO),
                "ACME Industria LTDA",
                "ACME",
                TipoSocietario.LTDA,
                PorteEmpresa.MEDIO);

        KybEmpresa salva = kybRepository.saveAndFlush(nova);
        KybEmpresa recarregada = kybRepository.findById(salva.getId()).orElseThrow();

        assertThat(recarregada.getSolicitacaoId()).isEqualTo(solicitacaoId);
        assertThat(recarregada.getCnpj()).isEqualTo(CNPJ_VALIDO);
        assertThat(recarregada.getRazaoSocial()).isEqualTo("ACME Industria LTDA");
        assertThat(recarregada.getNomeFantasia()).isEqualTo("ACME");
        assertThat(recarregada.getTipoSocietario()).isEqualTo(TipoSocietario.LTDA);
        assertThat(recarregada.getPorte()).isEqualTo(PorteEmpresa.MEDIO);
        assertThat(recarregada.getCriadoPor()).isEqualTo(AuditorAwareImpl.SYSTEM);
    }

    @Test
    void findBySolicitacaoIdLocalizaKyb() {
        kybRepository.saveAndFlush(KybEmpresa.criar(
                solicitacaoId, new Cnpj(CNPJ_VALIDO), "ACME LTDA", null, TipoSocietario.LTDA, PorteEmpresa.ME));

        assertThat(kybRepository.findBySolicitacaoId(solicitacaoId)).isPresent();
        assertThat(kybRepository.findBySolicitacaoId(UUID.randomUUID())).isEmpty();
    }
}
