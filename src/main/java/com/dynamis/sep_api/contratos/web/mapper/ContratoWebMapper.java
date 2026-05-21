package com.dynamis.sep_api.contratos.web.mapper;

import com.dynamis.sep_api.contratos.application.usecase.ConsultarStatusAssinaturaUseCase.StatusAssinaturaContrato;
import com.dynamis.sep_api.contratos.domain.model.AceiteContrato;
import com.dynamis.sep_api.contratos.domain.model.ClausulaContratual;
import com.dynamis.sep_api.contratos.domain.model.Contrato;
import com.dynamis.sep_api.contratos.domain.model.VersaoContrato;
import com.dynamis.sep_api.contratos.web.dto.AceiteContratoResponse;
import com.dynamis.sep_api.contratos.web.dto.ClausulaContratoResponse;
import com.dynamis.sep_api.contratos.web.dto.ContratoResponse;
import com.dynamis.sep_api.contratos.web.dto.StatusAssinaturaResponse;
import com.dynamis.sep_api.contratos.web.dto.VersaoContratoResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapeamento entre agregados de contratos e DTOs web via MapStruct (ADR 0006).
 *
 * <p>{@code toResponse(Contrato, AceiteContrato)} compoe a visao consolidada usada nos endpoints
 * {@code GET /api/v1/contratos/{id}} e {@code GET /api/v1/contratos/proposta/{propostaId}} —
 * aceite pode ser null se a versao vigente ainda nao foi aceita.
 */
@Mapper(componentModel = "spring")
public interface ContratoWebMapper {

    default ContratoResponse toResponse(Contrato contrato, AceiteContrato aceite) {
        VersaoContratoResponse vigente =
                contrato.versaoVigente().map(this::toVersaoResponse).orElse(null);
        AceiteContratoResponse aceiteResp = aceite == null ? null : toAceiteResponse(aceite);
        return new ContratoResponse(
                contrato.getId(),
                contrato.getPropostaId(),
                contrato.getTomadorId(),
                contrato.getTipo().name(),
                contrato.getStatus().name(),
                vigente,
                aceiteResp,
                contrato.getDataCriacao(),
                contrato.getDataModificacao());
    }

    default VersaoContratoResponse toVersaoResponse(VersaoContrato versao) {
        return new VersaoContratoResponse(
                versao.getId(),
                versao.getNumero(),
                versao.getConteudoTexto(),
                versao.getHashSha256(),
                versao.getDataGeracao(),
                versao.getParecerOrigemId(),
                versao.getClausulas().stream().map(this::toClausulaResponse).toList());
    }

    default ClausulaContratoResponse toClausulaResponse(ClausulaContratual clausula) {
        return new ClausulaContratoResponse(
                clausula.getId(), clausula.getOrdem(), clausula.getTitulo(), clausula.getTexto());
    }

    default AceiteContratoResponse toAceiteResponse(AceiteContrato aceite) {
        return new AceiteContratoResponse(
                aceite.getId(),
                aceite.getVersao().getId(),
                aceite.getTomadorId(),
                aceite.getDataAceite(),
                aceite.getIpOrigem(),
                aceite.getUserAgentOrigem());
    }

    default List<VersaoContratoResponse> toVersaoListResponse(List<VersaoContrato> versoes) {
        return versoes.stream().map(this::toVersaoResponse).toList();
    }

    default StatusAssinaturaResponse toStatusAssinaturaResponse(StatusAssinaturaContrato snapshot) {
        return new StatusAssinaturaResponse(
                snapshot.statusContrato().name(),
                snapshot.statusEnvelope() == null
                        ? null
                        : snapshot.statusEnvelope().name(),
                snapshot.idEnvelopeExterno(),
                snapshot.dataAtualizacaoProvider());
    }
}
