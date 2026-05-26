package com.dynamis.sep_api.backoffice.application.service;

import com.dynamis.sep_api.backoffice.domain.exception.LimiteReprocessoExcedidoException;
import com.dynamis.sep_api.backoffice.infrastructure.persistence.ReprocessoRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Conta reprocessos com mesmo {@code identificadorExterno} em janela de 24h (Sprint 14 Task 14.4).
 * Limite: 3 por entidade/24h — alem disso lanca {@link LimiteReprocessoExcedidoException} (429).
 */
@Service
public class AntiAbusoReprocessoService {

    private final ReprocessoRepository repository;
    private final Clock clock;

    public AntiAbusoReprocessoService(ReprocessoRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void validarLimite(String identificadorExterno) {
        OffsetDateTime corte = OffsetDateTime.now(clock).minusHours(24);
        long count = repository.countByIdentificadorExternoAndDataDisparoAfter(identificadorExterno, corte);
        if (count >= LimiteReprocessoExcedidoException.LIMITE_24H) {
            throw new LimiteReprocessoExcedidoException(identificadorExterno);
        }
    }
}
