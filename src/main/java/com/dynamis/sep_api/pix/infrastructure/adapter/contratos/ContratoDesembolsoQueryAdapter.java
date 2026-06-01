package com.dynamis.sep_api.pix.infrastructure.adapter.contratos;

import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.vo.StatusFormalizacao;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.pix.application.port.out.ContratoDesembolsoQueryPort;
import com.dynamis.sep_api.pix.application.port.out.dto.ContratoDesembolsoView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter que traduz {@link ContratoDesembolsoQueryPort} para os repositorios de {@code contratos} e
 * {@code credito}. O valor de desembolso vem da {@code PropostaCredito} associada ao contrato (o
 * agregado {@code Contrato} nao carrega valor). Encapsula a unica leitura inter-modulo de contrato
 * pelo {@code pix}; as camadas {@code application}/{@code domain} de pix ficam livres de conhecer
 * estes pacotes.
 */
@Component
public class ContratoDesembolsoQueryAdapter implements ContratoDesembolsoQueryPort {

    private static final Logger log = LoggerFactory.getLogger(ContratoDesembolsoQueryAdapter.class);

    private final ContratoRepository contratoRepository;
    private final PropostaCreditoRepository propostaRepository;

    public ContratoDesembolsoQueryAdapter(
            ContratoRepository contratoRepository, PropostaCreditoRepository propostaRepository) {
        this.contratoRepository = contratoRepository;
        this.propostaRepository = propostaRepository;
    }

    @Override
    public Optional<ContratoDesembolsoView> buscarPorContrato(UUID contratoId) {
        return contratoRepository.findById(contratoId).map(this::toView);
    }

    private ContratoDesembolsoView toView(Contrato contrato) {
        BigDecimal valorDesembolso = propostaRepository
                .findById(contrato.getPropostaId())
                .map(PropostaCredito::getValorSolicitado)
                .orElse(null);
        if (valorDesembolso == null) {
            // Integridade quebrada: contrato sem proposta correspondente. Nao bloqueia aqui (o use
            // case da Task 20.2 valida valor); apenas alerta.
            log.warn(
                    "Proposta {} do contrato {} nao encontrada ao montar valor de desembolso",
                    contrato.getPropostaId(),
                    contrato.getId());
        }
        boolean assinado = contrato.getStatus() == StatusFormalizacao.ASSINADO;
        return new ContratoDesembolsoView(
                contrato.getId(), contrato.getPropostaId(), contrato.getTomadorId(), valorDesembolso, assinado);
    }
}
