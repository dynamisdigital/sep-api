package com.dynamis.sep_api.onboarding.domain.model;

import com.dynamis.sep_api.onboarding.domain.exception.StatusOnboardingInvalidoException;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolicitacaoOnboardingTest {

    private static final String CPF_VALIDO = "52998224725";

    private SolicitacaoOnboarding novaSolicitacao() {
        return SolicitacaoOnboarding.criar(
                UUID.randomUUID(), new Cpf(CPF_VALIDO), "Joao da Silva", LocalDate.of(1990, 1, 1));
    }

    @Test
    void criarIniciaEmStatusIniciadoComRevisaoZero() {
        SolicitacaoOnboarding s = novaSolicitacao();

        assertThat(s.getId()).isNotNull();
        assertThat(s.getStatus()).isEqualTo(StatusOnboarding.INICIADO);
        assertThat(s.getRevisaoDocumentos()).isZero();
        assertThat(s.getCpf()).isEqualTo(CPF_VALIDO);
    }

    @Test
    void primeiroDocumentoTransicionaParaDocumentosRecebidosEIncrementaRevisao() {
        SolicitacaoOnboarding s = novaSolicitacao();

        s.registrarDocumentoEnviado();

        assertThat(s.getStatus()).isEqualTo(StatusOnboarding.DOCUMENTOS_RECEBIDOS);
        assertThat(s.getRevisaoDocumentos()).isEqualTo(1);
    }

    @Test
    void documentosAdicionaisMantemStatusEIncrementamRevisao() {
        SolicitacaoOnboarding s = novaSolicitacao();
        s.registrarDocumentoEnviado();

        s.registrarDocumentoEnviado();
        s.registrarDocumentoEnviado();

        assertThat(s.getStatus()).isEqualTo(StatusOnboarding.DOCUMENTOS_RECEBIDOS);
        assertThat(s.getRevisaoDocumentos()).isEqualTo(3);
    }

    @Test
    void marcarEmVerificacaoSoFunctionaApartirDeDocumentosRecebidos() {
        SolicitacaoOnboarding s = novaSolicitacao();
        s.registrarDocumentoEnviado();

        s.marcarEmVerificacao("ext-123");

        assertThat(s.getStatus()).isEqualTo(StatusOnboarding.EM_VERIFICACAO);
        assertThat(s.getIdVerificacaoExterna()).isEqualTo("ext-123");
    }

    @Test
    void marcarEmVerificacaoFalhaSeAindaEmIniciado() {
        SolicitacaoOnboarding s = novaSolicitacao();

        assertThatThrownBy(() -> s.marcarEmVerificacao("ext"))
                .isInstanceOf(StatusOnboardingInvalidoException.class);
    }

    @Test
    void finalizarTransicionaParaStatusFinalApartirDeEmVerificacao() {
        SolicitacaoOnboarding s = novaSolicitacao();
        s.registrarDocumentoEnviado();
        s.marcarEmVerificacao("ext");

        s.finalizar(StatusOnboarding.APROVADO);

        assertThat(s.getStatus()).isEqualTo(StatusOnboarding.APROVADO);
    }

    @Test
    void finalizarRejeitaStatusNaoFinal() {
        SolicitacaoOnboarding s = novaSolicitacao();
        s.registrarDocumentoEnviado();
        s.marcarEmVerificacao("ext");

        assertThatThrownBy(() -> s.finalizar(StatusOnboarding.INICIADO))
                .isInstanceOf(StatusOnboardingInvalidoException.class);
    }

    @Test
    void statusFinalBloqueiaNovosDocumentos() {
        SolicitacaoOnboarding s = novaSolicitacao();
        s.registrarDocumentoEnviado();
        s.marcarEmVerificacao("ext");
        s.finalizar(StatusOnboarding.REPROVADO);

        assertThatThrownBy(s::registrarDocumentoEnviado).isInstanceOf(StatusOnboardingInvalidoException.class);
    }
}
