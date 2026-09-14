package com.dynamis.sep_api.notificacao.domain.vo;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enquanto a regua de cobranca nao migrar, existem dois {@code CanalNotificacao} (ADR 0021,
 * consequencias negativas). Igualdade integral seria a guarda errada: o modulo novo tem de ter
 * {@code IN_APP} e a cobranca nao. A guarda certa e "a cobranca esta contida no novo, e a unica
 * diferenca e {@code IN_APP}".
 */
class CanalNotificacaoCompatibilidadeTest {

    private static Set<String> nomes(Class<? extends Enum<?>> tipo) {
        return Arrays.stream(tipo.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }

    @Test
    void todoCanalDaCobrancaExisteNoModuloDeNotificacao() {
        assertThat(nomes(CanalNotificacao.class))
                .containsAll(nomes(com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao.class));
    }

    @Test
    void aUnicaDiferencaEInApp() {
        Set<String> diferenca = new HashSet<>(nomes(CanalNotificacao.class));
        diferenca.removeAll(nomes(com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao.class));

        assertThat(diferenca).containsExactly("IN_APP");
    }
}
