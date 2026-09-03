package com.dynamis.sep_api.shared.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 35 Task 35.2. O corte deixou de ser detalhe do {@code RateLimitFilter} quando ganhou um
 * segundo consumidor, o {@code ContratoController}, cujo valor termina em
 * {@code audit_log_seguranca.ip}.
 */
class OrigemDaRequestTest {

    @Test
    void limiteBateComOTamanhoDasColunas() {
        assertThat(OrigemDaRequest.MAX_TAMANHO)
                .as("VARCHAR(45) em login_attempt.ip e audit_log_seguranca.ip; mudar aqui exige migration")
                .isEqualTo(45);
    }

    @Test
    void ipv6NoLimiteEhPreservado() {
        String ipv6 = "0000:0000:0000:0000:0000:ffff:192.168.100.228";
        assertThat(ipv6).hasSize(OrigemDaRequest.MAX_TAMANHO);

        assertThat(OrigemDaRequest.normalizar(ipv6)).isEqualTo(ipv6);
    }

    @Test
    void umCaractereAcimaDoLimiteJaEhCortado() {
        assertThat(OrigemDaRequest.normalizar("a".repeat(OrigemDaRequest.MAX_TAMANHO + 1)))
                .isEqualTo("unknown");
    }

    /**
     * O que o valve entrega atras de proxy confiavel nao e validado como IP: um token longo chega
     * inteiro no {@code getRemoteAddr()}. Sem colapsar no mesmo balde, o teto de entradas do mapa de
     * limitadores nao limita bytes.
     */
    @Test
    void origensGrandesEDiferentesColapsamNoMesmoBalde() {
        assertThat(OrigemDaRequest.normalizar("a".repeat(200)))
                .isEqualTo(OrigemDaRequest.normalizar("b".repeat(9000)))
                .isEqualTo("unknown");
    }

    @Test
    void nuloEBrancoViramDesconhecida() {
        assertThat(OrigemDaRequest.normalizar(null)).isEqualTo("unknown");
        assertThat(OrigemDaRequest.normalizar("")).isEqualTo("unknown");
        assertThat(OrigemDaRequest.normalizar("   ")).isEqualTo("unknown");
    }

    @Test
    void ipv4ComumPassaIntacto() {
        assertThat(OrigemDaRequest.normalizar("203.0.113.7")).isEqualTo("203.0.113.7");
    }
}
