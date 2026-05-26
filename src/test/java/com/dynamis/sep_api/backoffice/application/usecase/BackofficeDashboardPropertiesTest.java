package com.dynamis.sep_api.backoffice.application.usecase;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackofficeDashboardPropertiesTest {

    @Test
    void valoresValidos_constroi() {
        BackofficeDashboardProperties p = new BackofficeDashboardProperties("America/Sao_Paulo", 30, 48, 5);
        assertThat(p.zoneId()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
        assertThat(p.tempoMedioJanelaDias()).isEqualTo(30);
        assertThat(p.criticosThresholdHoras()).isEqualTo(48);
        assertThat(p.topTiposLimit()).isEqualTo(5);
    }

    @Test
    void timezone_blank_lanca() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BackofficeDashboardProperties("  ", 30, 48, 5));
        assertThatIllegalArgumentException().isThrownBy(() -> new BackofficeDashboardProperties(null, 30, 48, 5));
    }

    @Test
    void timezone_invalido_lanca() {
        assertThatThrownBy(() -> new BackofficeDashboardProperties("Nao/Existe", 30, 48, 5))
                .isInstanceOf(java.time.zone.ZoneRulesException.class);
    }

    @Test
    void thresholdsZeroOuNegativos_lanca() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BackofficeDashboardProperties("UTC", 0, 48, 5));
        assertThatIllegalArgumentException().isThrownBy(() -> new BackofficeDashboardProperties("UTC", 30, 0, 5));
        assertThatIllegalArgumentException().isThrownBy(() -> new BackofficeDashboardProperties("UTC", 30, 48, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new BackofficeDashboardProperties("UTC", -1, 48, 5));
    }
}
