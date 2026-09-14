package com.dynamis.sep_api.shared.exception;

/**
 * Registro dos prefixos de codigo de erro (ADR 0020 §1): cada prefixo e uma area funcional com um
 * unico modulo dono. Um modulo pode ter mais de um prefixo; um prefixo nunca aparece em dois
 * modulos. Codigo com prefixo fora daqui, ou usado fora do modulo dono, reprova o build
 * ({@code ConvencaoCodigosErroTest}).
 *
 * <p>{@code CRD} significa "credora", embora a leitura natural seja "credito": mudar custaria
 * renomear codigos publicados (ADR 0020, consequencias negativas). O credito usa {@code PRP}.
 */
public enum PrefixoCodigoErro {
    ASN("contratos", "assinatura digital"),
    AUTH("identity", "autenticacao e sessao"),
    BOF("backoffice", "fila e operacao de backoffice"),
    COB("cobranca", "cobranca e renegociacao"),
    CRD("credores", "credora: cadastro, oportunidade, interesse e aporte"),
    CTR("contratos", "formalizacao contratual"),
    GOV("governanca", "parametros e papeis"),
    MFA("identity", "segundo fator"),
    NTF("notificacao", "central de notificacoes do usuario"),
    ONB("onboarding", "KYC, KYB e PLD"),
    PIX("pix", "desembolso, recebimento e chaves"),
    PRP("credito", "proposta de credito e Open Finance"),
    USR("usuarios", "cadastro e senha de usuario"),
    WHK("shared", "recepcao de webhooks");

    private final String modulo;
    private final String area;

    PrefixoCodigoErro(String modulo, String area) {
        this.modulo = modulo;
        this.area = area;
    }

    /** Pacote de primeiro nivel sob {@code com.dynamis.sep_api} que e dono do prefixo. */
    public String modulo() {
        return modulo;
    }

    public String area() {
        return area;
    }
}
