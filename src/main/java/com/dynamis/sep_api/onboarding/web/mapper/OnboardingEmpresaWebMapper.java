package com.dynamis.sep_api.onboarding.web.mapper;

import com.dynamis.sep_api.onboarding.application.dto.StatusOnboardingEmpresaView;
import com.dynamis.sep_api.onboarding.domain.model.RepresentanteLegal;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cnpj;
import com.dynamis.sep_api.onboarding.web.dto.ConsultaPldResumoResponse;
import com.dynamis.sep_api.onboarding.web.dto.DocumentoEnviadoResponse;
import com.dynamis.sep_api.onboarding.web.dto.EmpresaResponse;
import com.dynamis.sep_api.onboarding.web.dto.RepresentanteLegalResponse;
import com.dynamis.sep_api.onboarding.web.dto.StatusOnboardingEmpresaResponse;
import org.mapstruct.Mapper;

/** MapStruct: dominio/view application -> DTOs web PJ. */
@Mapper
public interface OnboardingEmpresaWebMapper {

    default EmpresaResponse toEmpresaResponse(SolicitacaoOnboarding s) {
        String cnpjFormatado = formatarCnpj(s.getDocumento());
        return new EmpresaResponse(
                s.getId(),
                s.getStatus(),
                cnpjFormatado,
                s.getNomeCompleto(),
                s.getDataCriacao(),
                s.getDataModificacao());
    }

    default StatusOnboardingEmpresaResponse toStatusResponse(StatusOnboardingEmpresaView view) {
        var dados = new StatusOnboardingEmpresaResponse.DadosEmpresa(
                formatarCnpj(view.dadosEmpresa().cnpj()),
                view.dadosEmpresa().razaoSocial(),
                view.dadosEmpresa().nomeFantasia(),
                view.dadosEmpresa().tipoSocietario(),
                view.dadosEmpresa().porte());
        return new StatusOnboardingEmpresaResponse(
                view.id(),
                view.status(),
                view.dataCriacao(),
                view.dataModificacao(),
                dados,
                view.documentosEnviados().stream()
                        .map(d -> new DocumentoEnviadoResponse(d.id(), d.tipo(), d.dataEnvio(), d.sha256()))
                        .toList(),
                view.representantes().stream()
                        .map(this::toRepresentanteResponse)
                        .toList(),
                view.resultado() == null
                        ? null
                        : new StatusOnboardingEmpresaResponse.ResultadoResponse(
                                view.resultado().statusFinal(),
                                view.resultado().motivo(),
                                view.resultado().dataResultado()));
    }

    default RepresentanteLegalResponse toRepresentanteResponse(RepresentanteLegal r) {
        return new RepresentanteLegalResponse(
                r.getId(),
                r.getNome(),
                mascararCpf(r.getCpf()),
                r.getCargo(),
                new ConsultaPldResumoResponse(r.getStatusPld(), r.getDataConsultaPld()));
    }

    default RepresentanteLegalResponse toRepresentanteResponse(StatusOnboardingEmpresaView.RepresentanteView r) {
        return new RepresentanteLegalResponse(
                r.id(),
                r.nome(),
                mascararCpf(r.cpf()),
                r.cargo(),
                new ConsultaPldResumoResponse(r.statusPld(), r.dataConsultaPld()));
    }

    static String formatarCnpj(String cnpjBruto) {
        if (cnpjBruto == null) return null;
        String digitos = cnpjBruto.replaceAll("\\D", "");
        if (digitos.length() != 14) return cnpjBruto;
        return new Cnpj(digitos).formatado();
    }

    /** Mantem primeiros 3 + ultimos 2 digitos; mascara os 6 do meio. */
    static String mascararCpf(String cpf) {
        if (cpf == null || cpf.length() < 6) return "***";
        int len = cpf.length();
        return cpf.substring(0, 3) + "*".repeat(len - 5) + cpf.substring(len - 2);
    }
}
