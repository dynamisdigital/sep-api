package com.dynamis.sep_api.backoffice.domain.vo;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrioridadeItemTest {

    @Test
    void ordinalPeso_seguePrecedenciaDeNegocio() {
        assertThat(PrioridadeItem.CRITICA.ordinalPeso()).isEqualTo(4);
        assertThat(PrioridadeItem.ALTA.ordinalPeso()).isEqualTo(3);
        assertThat(PrioridadeItem.MEDIA.ordinalPeso()).isEqualTo(2);
        assertThat(PrioridadeItem.BAIXA.ordinalPeso()).isEqualTo(1);
    }

    @Test
    void ordinalPeso_ordenacaoDescColocaCriticaPrimeiro() {
        List<PrioridadeItem> ordenado = List.of(
                        PrioridadeItem.BAIXA, PrioridadeItem.CRITICA, PrioridadeItem.MEDIA, PrioridadeItem.ALTA)
                .stream()
                .sorted(Comparator.comparingInt(PrioridadeItem::ordinalPeso).reversed())
                .toList();

        assertThat(ordenado)
                .containsExactly(
                        PrioridadeItem.CRITICA, PrioridadeItem.ALTA, PrioridadeItem.MEDIA, PrioridadeItem.BAIXA);
    }
}
