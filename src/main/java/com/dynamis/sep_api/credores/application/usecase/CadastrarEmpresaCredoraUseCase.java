package com.dynamis.sep_api.credores.application.usecase;

import com.dynamis.sep_api.credores.application.dto.CadastrarEmpresaCredoraCommand;
import com.dynamis.sep_api.credores.application.dto.EmpresaCredoraView;
import com.dynamis.sep_api.credores.domain.event.EmpresaCredoraCadastradaEvent;
import com.dynamis.sep_api.credores.domain.event.EmpresaCredoraElegibilidadeDefinidaEvent;
import com.dynamis.sep_api.credores.domain.exception.CredoraJaCadastradaException;
import com.dynamis.sep_api.credores.domain.exception.OnboardingInvalidoParaCredoraException;
import com.dynamis.sep_api.credores.domain.exception.OwnershipCredoraException;
import com.dynamis.sep_api.credores.domain.model.EmpresaCredora;
import com.dynamis.sep_api.credores.domain.model.PerfilCredora;
import com.dynamis.sep_api.credores.domain.vo.StatusElegibilidade;
import com.dynamis.sep_api.credores.infrastructure.persistence.EmpresaCredoraRepository;
import com.dynamis.sep_api.credores.infrastructure.persistence.PerfilCredoraRepository;
import com.dynamis.sep_api.onboarding.application.query.ConsultarEmpresaParaCredoraQuery;
import com.dynamis.sep_api.onboarding.application.query.EmpresaParaCredoraResumo;
import com.dynamis.sep_api.onboarding.domain.exception.OnboardingNaoEncontradoException;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.TipoSolicitante;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastra uma {@link EmpresaCredora} para o usuario autenticado a partir de um onboarding PJ
 * aprovado (Sprint 16, Tasks 3 e 4). Pre-condicoes:
 *
 * <ul>
 *   <li>Onboarding referenciado deve existir (404);
 *   <li>Onboarding deve pertencer ao usuario autenticado — ownership (403);
 *   <li>Onboarding deve ser PJ com KYB registrado, fonte de {@code cnpj}/{@code razaoSocial} (422);
 *   <li>Usuario, onboarding e CNPJ nao podem ja ter credora vinculada (409).
 * </ul>
 *
 * <p>A elegibilidade nasce derivada do status do onboarding, sem reexecutar KYB/PLD: {@code
 * APROVADO_FINAL} -> ELEGIVEL (credora ATIVA); {@code REPROVADO}/{@code REPROVADO_PLD} ->
 * INELEGIVEL; demais status mantem PENDENTE.
 */
@Service
public class CadastrarEmpresaCredoraUseCase {

    private final EmpresaCredoraRepository empresaRepository;
    private final PerfilCredoraRepository perfilRepository;
    private final ConsultarEmpresaParaCredoraQuery onboardingQuery;
    private final ApplicationEventPublisher eventPublisher;

    public CadastrarEmpresaCredoraUseCase(
            EmpresaCredoraRepository empresaRepository,
            PerfilCredoraRepository perfilRepository,
            ConsultarEmpresaParaCredoraQuery onboardingQuery,
            ApplicationEventPublisher eventPublisher) {
        this.empresaRepository = empresaRepository;
        this.perfilRepository = perfilRepository;
        this.onboardingQuery = onboardingQuery;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public EmpresaCredoraView executar(CadastrarEmpresaCredoraCommand cmd) {
        EmpresaParaCredoraResumo resumo = onboardingQuery
                .consultarPorId(cmd.onboardingId())
                .orElseThrow(() -> new OnboardingNaoEncontradoException(cmd.onboardingId()));

        if (resumo.tipoSolicitante() != TipoSolicitante.EMPRESA) {
            throw OnboardingInvalidoParaCredoraException.naoEmpresa();
        }
        if (!resumo.usuarioId().equals(cmd.usuarioId())) {
            throw new OwnershipCredoraException();
        }
        if (resumo.cnpj() == null) {
            throw OnboardingInvalidoParaCredoraException.kybIncompleto();
        }
        if (empresaRepository.existsByUsuarioId(cmd.usuarioId())) {
            throw CredoraJaCadastradaException.porUsuario();
        }
        if (empresaRepository.existsByOnboardingId(cmd.onboardingId())) {
            throw CredoraJaCadastradaException.porOnboarding();
        }
        if (empresaRepository.existsByCnpj(resumo.cnpj())) {
            throw CredoraJaCadastradaException.porCnpj();
        }

        EmpresaCredora empresa =
                EmpresaCredora.cadastrar(cmd.usuarioId(), cmd.onboardingId(), resumo.cnpj(), resumo.razaoSocial());
        aplicarElegibilidade(empresa, resumo.status());
        empresaRepository.save(empresa);

        PerfilCredora perfil = PerfilCredora.criar(empresa.getId(), cmd.tipoCredora(), cmd.capacidadeAporte());
        perfilRepository.save(perfil);

        eventPublisher.publishEvent(
                new EmpresaCredoraCadastradaEvent(empresa.getId(), empresa.getUsuarioId(), empresa.getCnpj()));
        if (empresa.getElegibilidade() != StatusElegibilidade.PENDENTE) {
            eventPublisher.publishEvent(new EmpresaCredoraElegibilidadeDefinidaEvent(
                    empresa.getId(),
                    empresa.getUsuarioId(),
                    empresa.getElegibilidade(),
                    empresa.getMotivoInelegibilidade()));
        }

        return EmpresaCredoraView.de(empresa, perfil);
    }

    private void aplicarElegibilidade(EmpresaCredora empresa, StatusOnboarding status) {
        switch (status) {
            case APROVADO_FINAL -> empresa.registrarElegivel();
            case REPROVADO, REPROVADO_PLD -> empresa.registrarInelegivel("Onboarding em status " + status);
            default -> {
                // mantem PENDENTE ate o onboarding concluir
            }
        }
    }
}
