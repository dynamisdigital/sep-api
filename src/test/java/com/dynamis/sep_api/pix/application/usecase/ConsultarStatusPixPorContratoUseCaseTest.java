package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.StatusPixPublicoView;
import com.dynamis.sep_api.pix.domain.model.PixTransferencia;
import com.dynamis.sep_api.pix.domain.vo.StatusPixPublico;
import com.dynamis.sep_api.pix.domain.vo.StatusPixTransferencia;
import com.dynamis.sep_api.pix.infrastructure.persistence.PixTransferenciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsultarStatusPixPorContratoUseCaseTest {

    private PixTransferenciaRepository transferenciaRepository;
    private ConsultarStatusPixPorContratoUseCase useCase;

    @BeforeEach
    void setup() {
        transferenciaRepository = mock(PixTransferenciaRepository.class);
        useCase = new ConsultarStatusPixPorContratoUseCase(transferenciaRepository);
    }

    @Test
    void comDesembolso_retornaViewPublicaMapeada() {
        UUID contratoId = UUID.randomUUID();
        OffsetDateTime atualizadoEm = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        PixTransferencia transferencia = mock(PixTransferencia.class);
        when(transferencia.getStatus()).thenReturn(StatusPixTransferencia.CONCLUIDA);
        when(transferencia.getValor()).thenReturn(new BigDecimal("1500.00"));
        when(transferencia.getDataModificacao()).thenReturn(atualizadoEm);
        when(transferenciaRepository.findFirstByContratoIdOrderByDataCriacaoDesc(contratoId))
                .thenReturn(Optional.of(transferencia));

        StatusPixPublicoView view = useCase.executar(contratoId).orElseThrow();

        assertThat(view.status()).isEqualTo(StatusPixPublico.LIQUIDADO);
        assertThat(view.valor()).isEqualByComparingTo("1500.00");
        assertThat(view.atualizadoEm()).isEqualTo(atualizadoEm);
    }

    @Test
    void semDesembolso_retornaVazio() {
        UUID contratoId = UUID.randomUUID();
        when(transferenciaRepository.findFirstByContratoIdOrderByDataCriacaoDesc(contratoId))
                .thenReturn(Optional.empty());

        assertThat(useCase.executar(contratoId)).isEmpty();
    }
}
