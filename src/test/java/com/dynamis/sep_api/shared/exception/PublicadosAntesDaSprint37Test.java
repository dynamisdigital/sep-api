package com.dynamis.sep_api.shared.exception;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Criterio 8 da spec 037 e ADR 0020 §3: codigo publicado nao se renomeia nem sai do contrato. A
 * Sprint 37 normalizou a taxonomia inteira so acrescentando codigos; esta e a lista publicada ate a
 * Sprint 36 (Gate 37.0, {@code develop} {@code a774aa4}), e ela tem de continuar publicada, valor por
 * valor.
 *
 * <p>Pega o que as outras guardas nao pegam: renomear um codigo publicado <b>no fonte e no catalogo
 * ao mesmo tempo</b> mantem a particao verde (catalogo == aptos) e o gate verde (formato e prefixo
 * validos). So a lista do contrato anterior percebe que o valor antigo sumiu.
 */
class PublicadosAntesDaSprint37Test {

    private static final List<String> PUBLICADOS_ATE_A_SPRINT_36 = List.of(
            "ASN-400-002",
            "ASN-400-003",
            "AUTH-400-101",
            "AUTH-400-102",
            "AUTH-423-001",
            "BOF-400-001",
            "BOF-400-002",
            "BOF-404-001",
            "BOF-409-001",
            "BOF-429-001",
            "COB-400-001",
            "COB-403-001",
            "COB-404-001",
            "COB-404-002",
            "COB-404-003",
            "COB-409-001",
            "COB-409-003",
            "CRD-400-003",
            "CRD-400-004",
            "CRD-400-005",
            "CRD-400-006",
            "CRD-400-007",
            "CRD-400-008",
            "CRD-400-009",
            "CRD-400-010",
            "CRD-400-011",
            "CRD-400-012",
            "CRD-400-013",
            "CRD-400-014",
            "CRD-400-015",
            "CRD-404-003",
            "CRD-404-004",
            "CRD-404-005",
            "CRD-404-006",
            "CRD-404-007",
            "CRD-404-008",
            "CRD-409-001",
            "CRD-409-003",
            "CRD-409-004",
            "CRD-409-005",
            "CRD-409-006",
            "CRD-409-007",
            "CRD-422-004",
            "CTR-400-001",
            "CTR-403-001",
            "CTR-404-001",
            "CTR-409-001",
            "CTR-409-002",
            "CTR-409-003",
            "CTR-422-001",
            "CTR-422-002",
            "CTR-422-003",
            "CTR-422-004",
            "GOV-400-001",
            "GOV-404-001",
            "MFA-400-001",
            "MFA-400-002",
            "MFA-400-003",
            "MFA-400-004",
            "MFA-409-001",
            "ONB-400-001",
            "ONB-400-003",
            "ONB-400-005",
            "ONB-400-009",
            "ONB-400-010",
            "ONB-400-011",
            "ONB-400-012",
            "ONB-400-013",
            "ONB-400-016",
            "ONB-400-018",
            "ONB-404-002",
            "ONB-409-001",
            "ONB-409-002",
            "PIX-400-001",
            "PIX-404-001",
            "USR-403-001",
            "USR-403-002",
            "USR-404-001",
            "USR-409-001",
            "WHK-400-001");

    @Test
    void osOitentaCodigosPublicadosAteASprint36ContinuamPublicados() {
        assertThat(PUBLICADOS_ATE_A_SPRINT_36).hasSize(80).doesNotHaveDuplicates();

        assertThat(CatalogoCodigosErro.publicados())
                .as("codigo publicado nao se renomeia nem sai do contrato (ADR 0020 §3)")
                .containsAll(PUBLICADOS_ATE_A_SPRINT_36);
    }
}
