package com.dynamis.sep_api.backoffice.application.usecase;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackofficeDashboardPropertiesTest {

    @Test
    void timezone_valido_retornaZoneId() {
        BackofficeDashboardProperties p = new BackofficeDashboardProperties("America/Sao_Paulo");
        assertThat(p.zoneId()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
    }

    @Test
    void timezone_blank_lanca() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BackofficeDashboardProperties("  "));
        assertThatIllegalArgumentException().isThrownBy(() -> new BackofficeDashboardProperties(null));
    }

    @Test
    void timezone_invalido_lanca() {
        assertThatThrownBy(() -> new BackofficeDashboardProperties("Nao/Existe"))
                .isInstanceOf(java.time.zone.ZoneRulesException.class);
    }
}
