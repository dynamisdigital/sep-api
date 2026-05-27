package com.dynamis.sep_api.onboarding.infrastructure.persistence;

import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, AuditorAwareImpl.class})
@ActiveProfiles("dev")
class SolicitacaoOnboardingRepositoryTest {

    private static final String CPF_VALIDO = "52998224725";

    @Autowired
    private SolicitacaoOnboardingRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID usuarioId;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        usuarioRepository.deleteAll();
        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("kyc-pf@sep.test", "hash", Role.CLIENTE));
        usuarioId = u.getId();
    }

    @Test
    void persistirERecuperarSolicitacaoPreservaCampos() {
        SolicitacaoOnboarding nova =
                SolicitacaoOnboarding.criarPessoa(usuarioId, new Cpf(CPF_VALIDO), "Joao da Silva", LocalDate.of(1990, 1, 1));

        SolicitacaoOnboarding salva = repository.saveAndFlush(nova);
        SolicitacaoOnboarding recarregada = repository.findById(salva.getId()).orElseThrow();

        assertThat(recarregada.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(recarregada.getCpf()).isEqualTo(CPF_VALIDO);
        assertThat(recarregada.getStatus()).isEqualTo(StatusOnboarding.INICIADO);
        assertThat(recarregada.getRevisaoDocumentos()).isZero();
        assertThat(recarregada.getCriadoPor()).isEqualTo(AuditorAwareImpl.SYSTEM);
    }

    @Test
    void existsByDocumentoAndStatusInIdentificaSolicitacaoAtiva() {
        repository.saveAndFlush(
                SolicitacaoOnboarding.criarPessoa(usuarioId, new Cpf(CPF_VALIDO), "Joao", LocalDate.of(1990, 1, 1)));

        boolean ativo = repository.existsByDocumentoAndStatusIn(CPF_VALIDO, List.of(StatusOnboarding.INICIADO));
        boolean reprovado =
                repository.existsByDocumentoAndStatusIn(CPF_VALIDO, List.of(StatusOnboarding.REPROVADO));

        assertThat(ativo).isTrue();
        assertThat(reprovado).isFalse();
    }

    @Test
    void findByIdAndUsuarioIdRetornaSomenteParaOwner() {
        SolicitacaoOnboarding salva = repository.saveAndFlush(
                SolicitacaoOnboarding.criarPessoa(usuarioId, new Cpf(CPF_VALIDO), "Joao", LocalDate.of(1990, 1, 1)));

        assertThat(repository.findByIdAndUsuarioId(salva.getId(), usuarioId)).isPresent();
        assertThat(repository.findByIdAndUsuarioId(salva.getId(), UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    void existsByDocumentoAndStatusInIdentificaPfEPj() {
        repository.saveAndFlush(
                SolicitacaoOnboarding.criarPessoa(usuarioId, new Cpf(CPF_VALIDO), "Joao", LocalDate.of(1990, 1, 1)));

        String cnpj = "11222333000181";
        Usuario u2 = usuarioRepository.saveAndFlush(Usuario.criar("kyb-pj@sep.test", "hash", Role.CLIENTE));
        repository.saveAndFlush(SolicitacaoOnboarding.criarEmpresa(u2.getId(), cnpj, "ACME Industria LTDA"));

        assertThat(repository.existsByDocumentoAndStatusIn(CPF_VALIDO, List.of(StatusOnboarding.INICIADO)))
                .isTrue();
        assertThat(repository.existsByDocumentoAndStatusIn(cnpj, List.of(StatusOnboarding.INICIADO)))
                .isTrue();
        assertThat(repository.existsByDocumentoAndStatusIn("00000000000000", List.of(StatusOnboarding.INICIADO)))
                .isFalse();
    }

    @Test
    void criarEmpresaPersistidaPreservaTipoEDocumento() {
        String cnpj = "11222333000181";
        SolicitacaoOnboarding nova = SolicitacaoOnboarding.criarEmpresa(usuarioId, cnpj, "ACME Industria LTDA");

        SolicitacaoOnboarding salva = repository.saveAndFlush(nova);
        SolicitacaoOnboarding recarregada = repository.findById(salva.getId()).orElseThrow();

        assertThat(recarregada.getTipo()).isEqualTo(TipoSolicitante.EMPRESA);
        assertThat(recarregada.getDocumento()).isEqualTo(cnpj);
        assertThat(recarregada.getCpf()).isNull();
        assertThat(recarregada.getDataNascimento()).isNull();
        assertThat(recarregada.getNomeCompleto()).isEqualTo("ACME Industria LTDA");
    }

    @Test
    void findByIdVerificacaoExternaLocalizaPosDispatch() {
        SolicitacaoOnboarding s =
                SolicitacaoOnboarding.criarPessoa(usuarioId, new Cpf(CPF_VALIDO), "Joao", LocalDate.of(1990, 1, 1));
        s.registrarDocumentoEnviado();
        s.marcarEmVerificacao("ext-celcoin-001");
        repository.saveAndFlush(s);

        assertThat(repository.findByIdVerificacaoExterna("ext-celcoin-001")).isPresent();
        assertThat(repository.findByIdVerificacaoExterna("inexistente")).isEmpty();
    }
}
