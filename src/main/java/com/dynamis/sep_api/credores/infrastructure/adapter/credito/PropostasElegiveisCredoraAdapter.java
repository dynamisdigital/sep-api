package com.dynamis.sep_api.credores.infrastructure.adapter.credito;

import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.infrastructure.persistence.ContratoRepository;
import com.dynamis.sep_api.credito.domain.model.PropostaCredito;
import com.dynamis.sep_api.credito.domain.vo.StatusProposta;
import com.dynamis.sep_api.credito.infrastructure.persistence.PropostaCreditoRepository;
import com.dynamis.sep_api.credores.application.port.out.ConsultarPropostasElegiveisParaCredoraPort;
import com.dynamis.sep_api.credores.application.port.out.PropostaElegivelView;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Adapter de leitura que traduz propostas {@code APROVADA} do modulo {@code credito} em
 * {@link PropostaElegivelView} para o modulo {@code credores} (Sprint 17). Completa {@code
 * contratoId} a partir do modulo {@code contratos} quando ja existe contrato para a proposta.
 *
 * <p>Vive em {@code credores.infrastructure.adapter} e implementa uma porta orientada a necessidade
 * de {@code credores}; nao expoe entidades JPA de outros modulos. Mesmo padrao dos adapters de
 * leitura cross-module ja usados por {@code cobranca}.
 */
@Component
public class PropostasElegiveisCredoraAdapter implements ConsultarPropostasElegiveisParaCredoraPort {

    private final PropostaCreditoRepository propostaRepository;
    private final ContratoRepository contratoRepository;

    public PropostasElegiveisCredoraAdapter(
            PropostaCreditoRepository propostaRepository, ContratoRepository contratoRepository) {
        this.propostaRepository = propostaRepository;
        this.contratoRepository = contratoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropostaElegivelView> listarElegiveis() {
        return propostaRepository.findByStatus(StatusProposta.APROVADA, Pageable.unpaged()).stream()
                .map(this::mapear)
                .toList();
    }

    private PropostaElegivelView mapear(PropostaCredito p) {
        java.util.UUID contratoId = contratoRepository
                .findByPropostaId(p.getId())
                .map(Contrato::getId)
                .orElse(null);
        return new PropostaElegivelView(
                p.getId(),
                contratoId,
                p.getValorSolicitado(),
                p.getPrazoMeses(),
                p.getTaxaJurosMensal().orElse(null));
    }
}
