package com.dynamis.sep_api.onboarding.application.usecase;

import com.dynamis.sep_api.onboarding.domain.event.OnboardingIniciadoEvent;
import com.dynamis.sep_api.onboarding.domain.exception.CpfComOnboardingAtivoException;
import com.dynamis.sep_api.onboarding.domain.model.SolicitacaoOnboarding;
import com.dynamis.sep_api.onboarding.domain.vo.Cpf;
import com.dynamis.sep_api.onboarding.domain.vo.StatusOnboarding;
import com.dynamis.sep_api.onboarding.infrastructure.persistence.SolicitacaoOnboardingRepository;
import com.dynamis.sep_api.shared.exception.ValidacaoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

/**
 * Inicia uma solicitacao de onboarding KYC PF para o usuario autenticado. Rejeita se ja houver
 * solicitacao ativa para o mesmo CPF.
 */
@Service
public class IniciarOnboardingPessoaUseCase {

    private static final String CODIGO_CPF_INVALIDO = "ONB-400-002";

    private final SolicitacaoOnboardingRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public IniciarOnboardingPessoaUseCase(
            SolicitacaoOnboardingRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SolicitacaoOnboarding executar(
            UUID usuarioId, String cpfBruto, String nomeCompleto, LocalDate dataNascimento) {
        Cpf cpf = parsearCpf(cpfBruto);

        boolean jaAtivo = repository.existsByCpfAndStatusIn(
                cpf.valor(),
                Arrays.stream(StatusOnboarding.values())
                        .filter(StatusOnboarding::isAtivo)
                        .toList());
        if (jaAtivo) {
            throw new CpfComOnboardingAtivoException();
        }

        SolicitacaoOnboarding solicitacao = SolicitacaoOnboarding.criar(usuarioId, cpf, nomeCompleto, dataNascimento);
        SolicitacaoOnboarding salva = repository.save(solicitacao);
        eventPublisher.publishEvent(new OnboardingIniciadoEvent(salva.getId(), usuarioId));
        return salva;
    }

    private static Cpf parsearCpf(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            throw new ValidacaoException(CODIGO_CPF_INVALIDO, "CPF e obrigatorio");
        }
        try {
            return new Cpf(bruto);
        } catch (IllegalArgumentException ex) {
            throw new ValidacaoException(CODIGO_CPF_INVALIDO, ex.getMessage());
        }
    }
}
