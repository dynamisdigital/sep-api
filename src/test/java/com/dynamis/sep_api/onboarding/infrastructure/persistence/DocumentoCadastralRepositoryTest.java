package com.dynamis.sep_api.onboarding.infrastructure.persistence;

import com.dynamis.sep_api.onboarding.domain.model.DocumentoCadastral;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;
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
class DocumentoCadastralRepositoryTest {

    @Autowired
    private DocumentoCadastralRepository documentoRepository;

    @Autowired
    private SolicitacaoOnboardingRepository solicitacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UUID solicitacaoId;

    @BeforeEach
    void setup() {
        documentoRepository.deleteAll();
        solicitacaoRepository.deleteAll();
        usuarioRepository.deleteAll();
        Usuario u = usuarioRepository.saveAndFlush(Usuario.criar("kyc@sep.test", "hash", Role.CLIENTE));
        SolicitacaoOnboarding s = solicitacaoRepository.saveAndFlush(SolicitacaoOnboarding.criar(
                u.getId(), new Cpf("52998224725"), "Joao", LocalDate.of(1990, 1, 1)));
        solicitacaoId = s.getId();
    }

    @Test
    void existsBySolicitacaoIdAndTipoIdentificaTipoExistente() {
        DocumentoCadastral doc = DocumentoCadastral.criar(
                solicitacaoId, TipoDocumento.RG, new byte[] {1, 2, 3}, "image/jpeg", "rg.jpg", "abc123");
        documentoRepository.saveAndFlush(doc);

        assertThat(documentoRepository.existsBySolicitacaoIdAndTipo(solicitacaoId, TipoDocumento.RG)).isTrue();
        assertThat(documentoRepository.existsBySolicitacaoIdAndTipo(solicitacaoId, TipoDocumento.SELFIE)).isFalse();
    }

    @Test
    void findBySolicitacaoIdRetornaTodosOsDocumentos() {
        documentoRepository.saveAndFlush(DocumentoCadastral.criar(
                solicitacaoId, TipoDocumento.RG, new byte[] {1}, "image/jpeg", "rg.jpg", "h1"));
        documentoRepository.saveAndFlush(DocumentoCadastral.criar(
                solicitacaoId, TipoDocumento.SELFIE, new byte[] {2}, "image/png", "selfie.png", "h2"));

        assertThat(documentoRepository.findBySolicitacaoId(solicitacaoId)).hasSize(2);
    }
}
