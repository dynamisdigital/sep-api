package com.dynamis.sep_api.governanca.application.query;

import com.dynamis.sep_api.governanca.domain.model.ParametroOperacional;
import com.dynamis.sep_api.governanca.infrastructure.persistence.ParametroOperacionalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Implementacao read-only de {@link ParametroOperacionalReader}. Le o parametro ativo pela chave e
 * cai no {@code valorPadrao} quando ausente/inativo, garantindo que consumidores que ainda usam
 * properties nao quebrem durante a adocao incremental (Sprint 18).
 */
@Service
public class ParametroOperacionalReaderService implements ParametroOperacionalReader {

    private final ParametroOperacionalRepository repository;

    public ParametroOperacionalReaderService(ParametroOperacionalRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public int lerInteiro(String chave, int valorPadrao) {
        return valorAtivo(chave).map(Integer::parseInt).orElse(valorPadrao);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal lerDecimal(String chave, BigDecimal valorPadrao) {
        return valorAtivo(chave).map(BigDecimal::new).orElse(valorPadrao);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean lerBooleano(String chave, boolean valorPadrao) {
        return valorAtivo(chave).map(Boolean::parseBoolean).orElse(valorPadrao);
    }

    @Override
    @Transactional(readOnly = true)
    public String lerTexto(String chave, String valorPadrao) {
        return valorAtivo(chave).orElse(valorPadrao);
    }

    private Optional<String> valorAtivo(String chave) {
        return repository
                .findByChave(chave)
                .filter(ParametroOperacional::isAtivo)
                .map(ParametroOperacional::getValor);
    }
}
