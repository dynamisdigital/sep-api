package com.dynamis.sep_api.shared.exception;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0020 §3: codigo publicado nao se renomeia nem sai do contrato. Esta lista e a memoria do
 * contrato — todo codigo publicado ate o fechamento da Sprint 37 — e ela so cresce.
 *
 * <p>Pega o que as guardas estruturais nao pegam. Particao, gate e o {@code enum} do OpenAPI
 * verificam consistencia <b>no presente</b>: fonte, catalogo e documento concordam agora. Renomear um
 * codigo publicado no fonte e no catalogo ao mesmo tempo mantem as tres verdes; so a lista do que ja
 * foi publicado percebe que o valor antigo sumiu.
 *
 * <p><b>Manutencao</b>: no fechamento de cada sprint que publica codigo, acrescentar aqui os novos.
 * Os 80 publicados ate a Sprint 36 estao marcados no {@code CODIGOS-DE-ERRO.md}; a Sprint 37
 * acrescentou 63.
 */
class CodigosPublicadosNaoMudamTest {

    private static final List<String> PUBLICADOS_ATE_A_SPRINT_37 = List.of(
            "ASN-400-001",
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
            "COB-409-002",
            "COB-409-003",
            "COB-409-004",
            "CRD-400-001",
            "CRD-400-002",
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
            "CRD-403-001",
            "CRD-404-001",
            "CRD-404-002",
            "CRD-404-003",
            "CRD-404-004",
            "CRD-404-005",
            "CRD-404-006",
            "CRD-404-007",
            "CRD-404-008",
            "CRD-409-001",
            "CRD-409-002",
            "CRD-409-003",
            "CRD-409-004",
            "CRD-409-005",
            "CRD-409-006",
            "CRD-409-007",
            "CRD-422-001",
            "CRD-422-002",
            "CRD-422-003",
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
            "ONB-400-002",
            "ONB-400-003",
            "ONB-400-004",
            "ONB-400-005",
            "ONB-400-006",
            "ONB-400-007",
            "ONB-400-008",
            "ONB-400-009",
            "ONB-400-010",
            "ONB-400-011",
            "ONB-400-012",
            "ONB-400-013",
            "ONB-400-016",
            "ONB-400-017",
            "ONB-400-018",
            "ONB-400-019",
            "ONB-404-001",
            "ONB-404-002",
            "ONB-409-001",
            "ONB-409-002",
            "PIX-400-001",
            "PIX-400-003",
            "PIX-400-004",
            "PIX-400-005",
            "PIX-400-006",
            "PIX-400-007",
            "PIX-400-008",
            "PIX-400-009",
            "PIX-400-010",
            "PIX-404-001",
            "PIX-404-002",
            "PIX-404-003",
            "PIX-404-004",
            "PIX-404-005",
            "PIX-404-006",
            "PIX-404-007",
            "PIX-409-001",
            "PIX-409-002",
            "PIX-409-003",
            "PIX-409-004",
            "PIX-409-005",
            "PIX-422-001",
            "PIX-422-002",
            "PIX-422-003",
            "PIX-422-004",
            "PIX-422-005",
            "PIX-422-006",
            "PIX-422-007",
            "PIX-422-008",
            "PRP-400-001",
            "PRP-400-002",
            "PRP-403-001",
            "PRP-404-001",
            "PRP-404-002",
            "PRP-409-002",
            "PRP-422-001",
            "PRP-422-002",
            "PRP-422-003",
            "USR-400-001",
            "USR-400-002",
            "USR-400-003",
            "USR-400-004",
            "USR-403-001",
            "USR-403-002",
            "USR-404-001",
            "USR-409-001",
            "WHK-400-001",
            "WHK-400-003",
            "WHK-400-004",
            "WHK-400-005");

    @Test
    void nenhumCodigoJaPublicadoSaiDoCatalogo() {
        assertThat(PUBLICADOS_ATE_A_SPRINT_37).hasSize(143).doesNotHaveDuplicates();

        assertThat(CatalogoCodigosErro.publicados())
                .as("codigo publicado nao se renomeia nem sai do contrato (ADR 0020 §3)")
                .containsAll(PUBLICADOS_ATE_A_SPRINT_37);
    }
}
