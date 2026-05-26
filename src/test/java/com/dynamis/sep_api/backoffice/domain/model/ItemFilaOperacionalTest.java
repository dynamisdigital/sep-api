package com.dynamis.sep_api.backoffice.domain.model;

import com.dynamis.sep_api.backoffice.domain.exception.TransicaoItemInvalidaException;
import com.dynamis.sep_api.backoffice.domain.vo.PrioridadeItem;
import com.dynamis.sep_api.backoffice.domain.vo.StatusItemFila;
import com.dynamis.sep_api.backoffice.domain.vo.TipoEntidadeReferenciada;
import com.dynamis.sep_api.backoffice.domain.vo.TipoItemFila;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemFilaOperacionalTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 5, 26, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void abrir_devolveItemAberto() {
        ItemFilaOperacional item = abrirPadrao();

        assertThat(item.getId()).isNotNull();
        assertThat(item.getStatus()).isEqualTo(StatusItemFila.ABERTO);
        assertThat(item.getDataAbertura()).isEqualTo(AGORA);
        assertThat(item.getAtribuidoA()).isNull();
        assertThat(item.getDataResolucao()).isNull();
    }

    @Test
    void abrir_tituloVazio_lanca() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ItemFilaOperacional.abrir(
                        TipoItemFila.ONBOARDING_ERRO,
                        PrioridadeItem.ALTA,
                        TipoEntidadeReferenciada.ONBOARDING,
                        UUID.randomUUID(),
                        "  ",
                        null,
                        AGORA));
    }

    @Test
    void abrir_descricaoAcima4000_lanca() {
        String longa = "x".repeat(4001);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ItemFilaOperacional.abrir(
                        TipoItemFila.OUTRO,
                        PrioridadeItem.BAIXA,
                        TipoEntidadeReferenciada.OUTRO,
                        UUID.randomUUID(),
                        "titulo",
                        longa,
                        AGORA));
    }

    @Test
    void abrir_tituloAcima255_lanca() {
        String longo = "x".repeat(256);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ItemFilaOperacional.abrir(
                        TipoItemFila.OUTRO,
                        PrioridadeItem.BAIXA,
                        TipoEntidadeReferenciada.OUTRO,
                        UUID.randomUUID(),
                        longo,
                        null,
                        AGORA));
    }

    @Test
    void assumir_setaOperadorETransiciona() {
        ItemFilaOperacional item = abrirPadrao();
        UUID operador = UUID.randomUUID();

        item.assumir(operador);

        assertThat(item.getStatus()).isEqualTo(StatusItemFila.EM_TRATAMENTO);
        assertThat(item.getAtribuidoA()).isEqualTo(operador);
    }

    @Test
    void assumir_duasVezes_lanca() {
        ItemFilaOperacional item = abrirPadrao();
        item.assumir(UUID.randomUUID());

        assertThatExceptionOfType(TransicaoItemInvalidaException.class).isThrownBy(() -> item.assumir(UUID.randomUUID()));
    }

    @Test
    void resolver_apenasAposAssumir() {
        ItemFilaOperacional item = abrirPadrao();

        assertThatExceptionOfType(TransicaoItemInvalidaException.class).isThrownBy(() -> item.resolver(AGORA));

        item.assumir(UUID.randomUUID());
        item.resolver(AGORA.plusHours(1));

        assertThat(item.getStatus()).isEqualTo(StatusItemFila.RESOLVIDO);
        assertThat(item.getDataResolucao()).isEqualTo(AGORA.plusHours(1));
    }

    @Test
    void ignorar_aceitoEmAbertoOuEmTratamento() {
        ItemFilaOperacional aberto = abrirPadrao();
        aberto.ignorar(AGORA);
        assertThat(aberto.getStatus()).isEqualTo(StatusItemFila.IGNORADO);

        ItemFilaOperacional emTratamento = abrirPadrao();
        emTratamento.assumir(UUID.randomUUID());
        emTratamento.ignorar(AGORA.plusMinutes(10));
        assertThat(emTratamento.getStatus()).isEqualTo(StatusItemFila.IGNORADO);
    }

    @Test
    void ignorar_aposResolvido_lanca() {
        ItemFilaOperacional item = abrirPadrao();
        item.assumir(UUID.randomUUID());
        item.resolver(AGORA);

        assertThatExceptionOfType(TransicaoItemInvalidaException.class).isThrownBy(() -> item.ignorar(AGORA));
    }

    @Test
    void resolver_argumentoNulo_lanca() {
        ItemFilaOperacional item = abrirPadrao();
        item.assumir(UUID.randomUUID());

        assertThatThrownBy(() -> item.resolver(null)).isInstanceOf(NullPointerException.class);
    }

    private ItemFilaOperacional abrirPadrao() {
        return ItemFilaOperacional.abrir(
                TipoItemFila.ONBOARDING_ERRO,
                PrioridadeItem.ALTA,
                TipoEntidadeReferenciada.ONBOARDING,
                UUID.randomUUID(),
                "Onboarding REPROVADO",
                "Detalhe opcional",
                AGORA);
    }
}
