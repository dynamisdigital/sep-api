package com.dynamis.sep_api.notificacao.domain.vo;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Canal e situacao de entrega, com o instante da ultima mudanca e o motivo quando falhou (ADR 0021
 * §3). Imutavel: cada resultado de envio produz uma nova {@code Entrega}.
 *
 * <p>Invariantes: {@code IN_APP} so existe {@code DISPONIVEL}; {@code EMAIL} nunca e {@code
 * DISPONIVEL}; {@code SMS} nao tem entrega neste modulo; {@code motivoFalha} existe se, e somente
 * se, a situacao e {@code FALHOU}.
 */
public record Entrega(
        CanalNotificacao canal, SituacaoEntrega situacao, OffsetDateTime atualizadaEm, String motivoFalha) {

    static final int TAMANHO_MAXIMO_MOTIVO = 200;

    public Entrega {
        Objects.requireNonNull(canal, "canal obrigatorio");
        Objects.requireNonNull(situacao, "situacao obrigatoria");
        Objects.requireNonNull(atualizadaEm, "atualizadaEm obrigatorio");
        if (!situacaoAdmitidaPeloCanal(canal, situacao)) {
            throw new IllegalArgumentException("situacao " + situacao + " incompativel com o canal " + canal);
        }
        if ((situacao == SituacaoEntrega.FALHOU) != (motivoFalha != null)) {
            throw new IllegalArgumentException("motivoFalha existe se, e somente se, a situacao e FALHOU");
        }
        if (motivoFalha != null && (motivoFalha.isBlank() || motivoFalha.length() > TAMANHO_MAXIMO_MOTIVO)) {
            throw new IllegalArgumentException(
                    "motivoFalha deve ter entre 1 e " + TAMANHO_MAXIMO_MOTIVO + " caracteres");
        }
    }

    public static Entrega inAppDisponivel(OffsetDateTime agora) {
        return new Entrega(CanalNotificacao.IN_APP, SituacaoEntrega.DISPONIVEL, agora, null);
    }

    public static Entrega emailPendente(OffsetDateTime agora) {
        return new Entrega(CanalNotificacao.EMAIL, SituacaoEntrega.PENDENTE, agora, null);
    }

    public Entrega aposEnvio(OffsetDateTime agora) {
        return resultado(SituacaoEntrega.ENVIADA, null, agora);
    }

    public Entrega aposSimulacao(OffsetDateTime agora) {
        return resultado(SituacaoEntrega.SIMULADA, null, agora);
    }

    public Entrega aposFalha(String motivo, OffsetDateTime agora) {
        return resultado(SituacaoEntrega.FALHOU, motivo, agora);
    }

    /** So entrega {@code PENDENTE} recebe resultado: o resultado de um envio e registrado uma vez. */
    private Entrega resultado(SituacaoEntrega nova, String motivo, OffsetDateTime agora) {
        if (situacao != SituacaoEntrega.PENDENTE) {
            throw new IllegalStateException("resultado de envio exige entrega PENDENTE; situacao atual: " + situacao);
        }
        return new Entrega(canal, nova, agora, motivo);
    }

    private static boolean situacaoAdmitidaPeloCanal(CanalNotificacao canal, SituacaoEntrega situacao) {
        return switch (canal) {
            case IN_APP -> situacao == SituacaoEntrega.DISPONIVEL;
            case EMAIL -> situacao != SituacaoEntrega.DISPONIVEL;
            case SMS -> false;
        };
    }
}
