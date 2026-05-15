package com.dynamis.sep_api.onboarding.infrastructure.persistence;

import com.dynamis.sep_api.onboarding.domain.model.ConsultaCNPJ;
import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cnpj;
import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.SituacaoCadastral;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class ConsultaCNPJRepositoryTest {

    private static final String CNPJ_VALIDO = "11222333000181";

    @Autowired
    private ConsultaCNPJRepository consultaRepository;

    @Autowired
    private KybEmpresaRepository kybRepository;

    @Autowired
    private SolicitacaoOnboardingRepository solicitacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID kybEmpresaId;

    @BeforeEach
    void setup() {
        consultaRepository.deleteAll();
        kybRepository.deleteAll();
        solicitacaoRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("kyb-cnpj@sep.test", "hash", Role.CLIENTE));
        SolicitacaoOnboarding s = solicitacaoRepository.saveAndFlush(
                SolicitacaoOnboarding.criarEmpresa(u.getId(), CNPJ_VALIDO, "ACME LTDA"));
        KybEmpresa kyb = kybRepository.saveAndFlush(KybEmpresa.criar(
                s.getId(), new Cnpj(CNPJ_VALIDO), "ACME LTDA", null, TipoSocietario.LTDA, PorteEmpresa.MEDIO));
        kybEmpresaId = kyb.getId();
    }

    @Test
    void persistirConsultaCnpjPreservaCamposEPayloadJsonb() {
        String payload = "{\"situacao\":\"ATIVA\",\"capitalSocial\":1000000.50}";
        ConsultaCNPJ consulta = ConsultaCNPJ.registrar(
                kybEmpresaId,
                SituacaoCadastral.ATIVA,
                "ACME Industria LTDA",
                "ACME",
                "62.01-5-01",
                "62.09-1-00,63.11-9-00",
                new BigDecimal("1000000.50"),
                LocalDate.of(2010, 1, 15),
                payload);

        ConsultaCNPJ salva = consultaRepository.saveAndFlush(consulta);
        ConsultaCNPJ recarregada = consultaRepository.findById(salva.getId()).orElseThrow();

        assertThat(recarregada.getKybEmpresaId()).isEqualTo(kybEmpresaId);
        assertThat(recarregada.getSituacaoCadastral()).isEqualTo(SituacaoCadastral.ATIVA);
        assertThat(recarregada.getRazaoSocial()).isEqualTo("ACME Industria LTDA");
        assertThat(recarregada.getNomeFantasia()).isEqualTo("ACME");
        assertThat(recarregada.getCnaePrincipal()).isEqualTo("62.01-5-01");
        assertThat(recarregada.getCnaesSecundarios()).isEqualTo("62.09-1-00,63.11-9-00");
        assertThat(recarregada.getCapitalSocial()).isEqualByComparingTo(new BigDecimal("1000000.50"));
        assertThat(recarregada.getDataAbertura()).isEqualTo(LocalDate.of(2010, 1, 15));
        assertThat(recarregada.getPayloadProvider()).contains("\"situacao\"").contains("\"capitalSocial\"");
        assertThat(recarregada.getDataConsulta()).isNotNull();
    }

    @Test
    void findByKybEmpresaIdLocalizaConsulta() {
        consultaRepository.saveAndFlush(ConsultaCNPJ.registrar(
                kybEmpresaId, SituacaoCadastral.ATIVA, "ACME", null, null, null, null, null, "{}"));

        assertThat(consultaRepository.findByKybEmpresaId(kybEmpresaId)).isPresent();
        assertThat(consultaRepository.findByKybEmpresaId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void unicidadeKybEmpresaIdImpedeMaisDeUmaConsultaPorEmpresa() {
        consultaRepository.saveAndFlush(ConsultaCNPJ.registrar(
                kybEmpresaId, SituacaoCadastral.ATIVA, "ACME", null, null, null, null, null, "{}"));

        ConsultaCNPJ duplicada = ConsultaCNPJ.registrar(
                kybEmpresaId, SituacaoCadastral.SUSPENSA, "ACME 2", null, null, null, null, null, "{}");

        assertThatThrownBy(() -> consultaRepository.saveAndFlush(duplicada))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aceitaSituacoesCadastraisAlemDeAtiva() {
        ConsultaCNPJ suspensa = ConsultaCNPJ.registrar(
                kybEmpresaId, SituacaoCadastral.SUSPENSA, null, null, null, null, null, null, "{}");

        ConsultaCNPJ salva = consultaRepository.saveAndFlush(suspensa);
        assertThat(salva.getSituacaoCadastral()).isEqualTo(SituacaoCadastral.SUSPENSA);
        assertThat(salva.getSituacaoCadastral().habilitaProgressao()).isFalse();
    }
}
