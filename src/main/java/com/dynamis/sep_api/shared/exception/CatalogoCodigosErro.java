package com.dynamis.sep_api.shared.exception;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Catalogo dos codigos de erro publicados no contrato (Sprint 36 Task 36.5).
 *
 * <p>Fonte unica do que o OpenAPI declara e do que o teste de contrato confere. Um codigo so entra
 * aqui se satisfizer os tres criterios do perimetro da Spec 036: formato canonico
 * {@code MOD-STATUS-NNN}, identificar <b>uma</b> condicao e ser alcancavel em runtime ate a
 * montagem do corpo de erro.
 *
 * <p><b>Por que uma lista literal, e nao referencias as constantes.</b> O Gate 36.0 mediu que 46 dos
 * 133 codigos existem so como literal inline, sem constante nomeada, e que 19 dos publicados sao
 * {@code private} — os tres {@code MFA-400-00x} inclusive. Referenciar as constantes exigiria
 * alargar visibilidade em 19 classes, que a Spec 036 §Fora proibe. A nao-divergencia em relacao ao
 * codigo-fonte e garantida por verificacao executavel, o script de particao da Task 36.7, e nao pelo
 * compilador. E uma escolha declarada, nao um descuido.
 *
 * <p><b>Acrescentar codigo e compativel; renomear codigo publicado e mudanca de contrato.</b> Depois
 * desta sprint o par {@code codigo + traceId} e o identificador que o usuario reporta ao suporte.
 *
 * <p>Fora do catalogo ficam os codigos que falham no perimetro, cada um registrado com motivo em
 * {@code docs-SEP/repos/sep-api/CODIGOS-DE-ERRO.md}. A Sprint 37 (ADR 0020) os normaliza e publica os
 * que ficam aptos — por isso este texto nao carrega contagem: ela muda a cada Task.
 *
 * <p><b>Sete dos 23 entraram no code review de fechamento</b>, e sao de um tipo que a primeira
 * versao desta sprint nao media: colidem <b>dentro da mesma classe</b>. O caso que os nomeia e
 * {@code ONB-400-004}, constante batizada {@code CODIGO_TAMANHO_EXCEDIDO} e lancada tambem para
 * "conteudo do documento e obrigatorio". A particao contava classes donas, e classe nao implica
 * condicao — ver {@code ParticaoDeCodigosErroTest}.
 */
public final class CatalogoCodigosErro {

    private static final Pattern CANONICO = Pattern.compile("^[A-Z]{3,4}-[0-9]{3}-[0-9]{3}$");

    private static final Set<String> PUBLICADOS = validar(List.of(
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
            "NTF-400-001",
            "NTF-404-001",
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
            "WHK-400-005"));

    private CatalogoCodigosErro() {}

    /** Ordenado, para o documento OpenAPI nao mudar de diff a cada regeracao. */
    public static Set<String> publicados() {
        return PUBLICADOS;
    }

    /**
     * Falha no carregamento da classe, e nao num teste: um codigo duplicado ou fora do formato
     * canonico no catalogo e defeito de contrato, e deixar a aplicacao subir com ele publicaria o
     * defeito.
     */
    private static Set<String> validar(List<String> codigos) {
        Set<String> unicos = new LinkedHashSet<>();
        for (String codigo : codigos) {
            if (!CANONICO.matcher(codigo).matches()) {
                throw new IllegalStateException("Codigo fora do formato canonico MOD-STATUS-NNN: " + codigo);
            }
            if (!unicos.add(codigo)) {
                throw new IllegalStateException("Codigo duplicado no catalogo: " + codigo);
            }
        }
        return Collections.unmodifiableSet(new TreeSet<>(unicos));
    }
}
