package com.dynamis.sep_api.cobranca.domain.model;

import com.dynamis.sep_api.cobranca.domain.vo.CanalNotificacao;
import com.dynamis.sep_api.cobranca.domain.vo.StatusEventoCobranca;
import com.dynamis.sep_api.cobranca.domain.vo.TipoEventoCobranca;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventoCobrancaTest {

    private static final UUID PARCELA = UUID.randomUUID();
    private static final UUID FINANCEIRO = UUID.randomUUID();
    private static final OffsetDateTime AGORA = OffsetDateTime.now();

    @Test
    void notificacaoAutomatica_camposObrigatorios() {
        EventoCobranca e = EventoCobranca.notificacaoAutomatica(
                PARCELA, CanalNotificacao.EMAIL, "email-amigavel", 0, StatusEventoCobranca.SUCESSO, null, AGORA);

        assertThat(e.getTipo()).isEqualTo(TipoEventoCobranca.NOTIFICACAO_AUTOMATICA);
        assertThat(e.getCanal()).isEqualTo(CanalNotificacao.EMAIL);
        assertThat(e.getTemplate()).isEqualTo("email-amigavel");
        assertThat(e.getDiasAtraso()).isZero();
        assertThat(e.getStatus()).isEqualTo(StatusEventoCobranca.SUCESSO);
        assertThat(e.getRegistradoPor()).isNull();
        assertThat(e.getId()).isNotNull();
    }

    @Test
    void notificacaoAutomatica_templateVazio_rejeita() {
        assertThatThrownBy(() -> EventoCobranca.notificacaoAutomatica(
                        PARCELA, CanalNotificacao.SMS, "  ", 5, StatusEventoCobranca.SUCESSO, null, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template");
    }

    @Test
    void contatoManual_exigeFinanceiroEDescricao() {
        EventoCobranca e =
                EventoCobranca.contatoManual(PARCELA, FINANCEIRO, 10, "Cliente confirmou pagamento ate sexta", AGORA);

        assertThat(e.getTipo()).isEqualTo(TipoEventoCobranca.CONTATO_MANUAL);
        assertThat(e.getCanal()).isNull();
        assertThat(e.getTemplate()).isNull();
        assertThat(e.getRegistradoPor()).isEqualTo(FINANCEIRO);
        assertThat(e.getDescricao()).contains("sexta");
    }

    @Test
    void contatoManual_semDescricao_rejeita() {
        assertThatThrownBy(() -> EventoCobranca.contatoManual(PARCELA, FINANCEIRO, 10, " ", AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("descricao");
    }

    @Test
    void mudancaEstado_naoAceitaNotificacaoOuContatoManual() {
        assertThatThrownBy(() -> EventoCobranca.mudancaEstado(
                        PARCELA, TipoEventoCobranca.NOTIFICACAO_AUTOMATICA, 0, null, null, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo invalido");
        assertThatThrownBy(() ->
                        EventoCobranca.mudancaEstado(PARCELA, TipoEventoCobranca.CONTATO_MANUAL, 0, null, null, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo invalido");
    }

    @Test
    void mudancaEstado_parcelaInadimplente_sucessoSemFinanceiro() {
        EventoCobranca e = EventoCobranca.mudancaEstado(
                PARCELA, TipoEventoCobranca.PARCELA_INADIMPLENTE, 90, "transicao automatica", null, AGORA);

        assertThat(e.getTipo()).isEqualTo(TipoEventoCobranca.PARCELA_INADIMPLENTE);
        assertThat(e.getStatus()).isEqualTo(StatusEventoCobranca.SUCESSO);
        assertThat(e.getDiasAtraso()).isEqualTo(90);
        assertThat(e.getRegistradoPor()).isNull();
    }
}
