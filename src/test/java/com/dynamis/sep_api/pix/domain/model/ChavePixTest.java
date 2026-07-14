package com.dynamis.sep_api.pix.domain.model;

import com.dynamis.sep_api.pix.domain.vo.StatusChavePix;
import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChavePixTest {

    private static final UUID CONTA = UUID.randomUUID();
    private static final UUID OPERADOR = UUID.randomUUID();
    private static final UUID REMOVEDOR = UUID.randomUUID();
    private static final String VALOR_BRUTO = "usuario@empresa.com";
    private static final String HASH = "a".repeat(64);
    private static final String MASCARA = "us***************om";
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 7, 14, 10, 0, 0, 0, ZoneOffset.UTC);

    private ChavePix cadastrar() {
        return ChavePix.cadastrar(CONTA, TipoChavePix.EMAIL, HASH, MASCARA, "prov-key-1", "idem-1", OPERADOR, AGORA);
    }

    @Test
    void cadastrar_nasceAtivaComCamposMinimizados() {
        ChavePix chave = cadastrar();

        assertThat(chave.getId()).isNotNull();
        assertThat(chave.getContaEscrowId()).isEqualTo(CONTA);
        assertThat(chave.getTipo()).isEqualTo(TipoChavePix.EMAIL);
        assertThat(chave.getStatus()).isEqualTo(StatusChavePix.ATIVA);
        assertThat(chave.getValorHash()).isEqualTo(HASH);
        assertThat(chave.getValorMascarado()).isEqualTo(MASCARA);
        assertThat(chave.getProviderKeyId()).isEqualTo("prov-key-1");
        assertThat(chave.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(chave.getCriadaPorUsuarioId()).isEqualTo(OPERADOR);
        assertThat(chave.getCriadaEm()).isEqualTo(AGORA);
        assertThat(chave.getRemovidaPorUsuarioId()).isNull();
        assertThat(chave.getRemovidaEm()).isNull();
    }

    @Test
    void inativar_transicionaERegistraAtor() {
        ChavePix chave = cadastrar();
        OffsetDateTime instante = AGORA.plusDays(1);

        boolean mudou = chave.inativar(REMOVEDOR, instante);

        assertThat(mudou).isTrue();
        assertThat(chave.getStatus()).isEqualTo(StatusChavePix.INATIVA);
        assertThat(chave.getRemovidaPorUsuarioId()).isEqualTo(REMOVEDOR);
        assertThat(chave.getRemovidaEm()).isEqualTo(instante);
    }

    @Test
    void inativar_jaInativa_eNoOpIdempotente() {
        ChavePix chave = cadastrar();
        chave.inativar(REMOVEDOR, AGORA.plusDays(1));

        boolean mudou = chave.inativar(UUID.randomUUID(), AGORA.plusDays(2));

        assertThat(mudou).isFalse();
        assertThat(chave.getStatus()).isEqualTo(StatusChavePix.INATIVA);
        assertThat(chave.getRemovidaPorUsuarioId()).isEqualTo(REMOVEDOR);
        assertThat(chave.getRemovidaEm()).isEqualTo(AGORA.plusDays(1));
    }

    @Test
    void inativar_semAtorOuInstante_rejeita() {
        assertThatThrownBy(() -> cadastrar().inativar(null, AGORA)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> cadastrar().inativar(REMOVEDOR, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void entidadeEToString_naoCarregamValorBruto() {
        ChavePix chave = cadastrar();

        assertThat(chave.toString()).doesNotContain(VALOR_BRUTO);
        assertThat(chave.getValorMascarado()).isNotEqualTo(VALOR_BRUTO);
    }

    @Test
    void cadastrar_camposObrigatoriosNulos_rejeita() {
        assertThatThrownBy(() -> ChavePix.cadastrar(
                        null, TipoChavePix.EMAIL, HASH, MASCARA, "prov-key-1", "idem-1", OPERADOR, AGORA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> ChavePix.cadastrar(CONTA, null, HASH, MASCARA, "prov-key-1", "idem-1", OPERADOR, AGORA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ChavePix.cadastrar(
                        CONTA, TipoChavePix.EMAIL, HASH, MASCARA, "prov-key-1", "idem-1", null, AGORA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ChavePix.cadastrar(
                        CONTA, TipoChavePix.EMAIL, HASH, MASCARA, "prov-key-1", "idem-1", OPERADOR, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void cadastrar_hashForaDoFormato_rejeita() {
        assertThatThrownBy(() -> ChavePix.cadastrar(
                        CONTA, TipoChavePix.EMAIL, "curto", MASCARA, "prov-key-1", "idem-1", OPERADOR, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cadastrar_mascaraVaziaOuAcimaDoLimite_rejeita() {
        assertThatThrownBy(() -> ChavePix.cadastrar(
                        CONTA, TipoChavePix.EMAIL, HASH, "  ", "prov-key-1", "idem-1", OPERADOR, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChavePix.cadastrar(
                        CONTA, TipoChavePix.EMAIL, HASH, "m".repeat(81), "prov-key-1", "idem-1", OPERADOR, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cadastrar_providerKeyIdOuIdempotencyKeyInvalidos_rejeita() {
        assertThatThrownBy(() ->
                        ChavePix.cadastrar(CONTA, TipoChavePix.EMAIL, HASH, MASCARA, " ", "idem-1", OPERADOR, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChavePix.cadastrar(
                        CONTA, TipoChavePix.EMAIL, HASH, MASCARA, "prov-key-1", " ", OPERADOR, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChavePix.cadastrar(
                        CONTA, TipoChavePix.EMAIL, HASH, MASCARA, "p".repeat(101), "idem-1", OPERADOR, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChavePix.cadastrar(
                        CONTA, TipoChavePix.EMAIL, HASH, MASCARA, "prov-key-1", "i".repeat(101), OPERADOR, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
