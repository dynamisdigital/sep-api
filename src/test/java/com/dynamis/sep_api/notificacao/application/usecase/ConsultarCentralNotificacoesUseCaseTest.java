package com.dynamis.sep_api.notificacao.application.usecase;

import com.dynamis.sep_api.notificacao.application.exception.PaginacaoInvalidaException;
import com.dynamis.sep_api.notificacao.application.port.out.CentralNotificacoesPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Page;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConsultarCentralNotificacoesUseCaseTest {

    private static final UUID USUARIO = UUID.randomUUID();

    private final CentralNotificacoesPort central = mock(CentralNotificacoesPort.class);
    private final ConsultarCentralNotificacoesUseCase useCase = new ConsultarCentralNotificacoesUseCase(central);

    @ParameterizedTest
    @CsvSource({"-1, 20", "0, 0", "0, -5", "0, 101"})
    void paginacaoForaDosLimites_eRecusadaComCodigoPublicado(int pagina, int tamanho) {
        assertThatThrownBy(() -> useCase.listar(USUARIO, pagina, tamanho))
                .isInstanceOf(PaginacaoInvalidaException.class)
                .extracting("codigo")
                .isEqualTo("NTF-400-001");
        verifyNoInteractions(central);
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "0, 100", "7, 20"})
    void paginacaoNosLimites_consultaApenasOUsuarioInformado(int pagina, int tamanho) {
        Page<com.dynamis.sep_api.notificacao.domain.model.Notificacao> vazia = Page.empty();
        when(central.listar(USUARIO, pagina, tamanho)).thenReturn(vazia);

        assertThat(useCase.listar(USUARIO, pagina, tamanho)).isSameAs(vazia);
        verify(central).listar(USUARIO, pagina, tamanho);
    }

    @Test
    void contador_eDoUsuarioInformado() {
        when(central.contarNaoLidas(USUARIO)).thenReturn(3L);

        assertThat(useCase.contarNaoLidas(USUARIO)).isEqualTo(3L);
    }
}
