package com.dynamis.sep_api.onboarding.infrastructure.persistence;

import com.dynamis.sep_api.onboarding.domain.model.KybEmpresa;
import com.dynamis.sep_api.onboarding.domain.model.RepresentanteLegal;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cnpj;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.PorteEmpresa;
import com.dynamis.sep_api.onboarding.domain.vo.StatusPldRepresentante;
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
class RepresentanteLegalRepositoryTest {

    private static final String CNPJ_VALIDO = "11222333000181";
    private static final String CPF_VALIDO = "52998224725";

    @Autowired
    private RepresentanteLegalRepository representanteRepository;

    @Autowired
    private KybEmpresaRepository kybRepository;

    @Autowired
    private SolicitacaoOnboardingRepository solicitacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID kybEmpresaId;

    @BeforeEach
    void setup() {
        representanteRepository.deleteAll();
        kybRepository.deleteAll();
        solicitacaoRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("kyb-rep@sep.test", "hash", Role.CLIENTE));
        SolicitacaoOnboarding s = solicitacaoRepository.saveAndFlush(
                SolicitacaoOnboarding.criarEmpresa(u.getId(), CNPJ_VALIDO, "ACME LTDA"));
        KybEmpresa kyb = kybRepository.saveAndFlush(KybEmpresa.criar(
                s.getId(), new Cnpj(CNPJ_VALIDO), "ACME LTDA", null, TipoSocietario.LTDA, PorteEmpresa.ME));
        kybEmpresaId = kyb.getId();
    }

    @Test
    void persistirRepresentantePreservaCampos() {
        RepresentanteLegal rep = RepresentanteLegal.criar(kybEmpresaId, "Joao Silva", new Cpf(CPF_VALIDO), "Diretor");

        RepresentanteLegal salvo = representanteRepository.saveAndFlush(rep);
        RepresentanteLegal recarregado =
                representanteRepository.findById(salvo.getId()).orElseThrow();

        assertThat(recarregado.getKybEmpresaId()).isEqualTo(kybEmpresaId);
        assertThat(recarregado.getNome()).isEqualTo("Joao Silva");
        assertThat(recarregado.getCpf()).isEqualTo(CPF_VALIDO);
        assertThat(recarregado.getCargo()).isEqualTo("Diretor");
        assertThat(recarregado.getStatusPld()).isEqualTo(StatusPldRepresentante.PENDENTE);
        assertThat(recarregado.getDataRegistro()).isNotNull();
        assertThat(recarregado.getDataConsultaPld()).isNull();
    }

    @Test
    void findByKybEmpresaIdRetornaTodosOsRepresentantes() {
        representanteRepository.saveAndFlush(
                RepresentanteLegal.criar(kybEmpresaId, "Joao", new Cpf(CPF_VALIDO), "CEO"));
        representanteRepository.saveAndFlush(
                RepresentanteLegal.criar(kybEmpresaId, "Maria", new Cpf("11144477735"), "CFO"));

        assertThat(representanteRepository.findByKybEmpresaId(kybEmpresaId)).hasSize(2);
    }

    @Test
    void transicaoStatusPldAtualizaDataConsulta() {
        RepresentanteLegal rep = representanteRepository.saveAndFlush(
                RepresentanteLegal.criar(kybEmpresaId, "Joao", new Cpf(CPF_VALIDO), "CEO"));

        rep.marcarPldHit();
        representanteRepository.saveAndFlush(rep);

        RepresentanteLegal recarregado =
                representanteRepository.findById(rep.getId()).orElseThrow();
        assertThat(recarregado.getStatusPld()).isEqualTo(StatusPldRepresentante.HIT);
        assertThat(recarregado.getDataConsultaPld()).isNotNull();
    }
}
