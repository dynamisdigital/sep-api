package com.dynamis.sep_api.pix.application.usecase;

import com.dynamis.sep_api.pix.application.dto.ChavePixItemResult;
import com.dynamis.sep_api.pix.application.port.out.ContaOperacionalEscrowQueryPort;
import com.dynamis.sep_api.pix.application.port.out.dto.ContaOperacionalEscrowView;
import com.dynamis.sep_api.pix.application.service.ChavePixSeguranca;
import com.dynamis.sep_api.pix.domain.model.ChavePix;
import com.dynamis.sep_api.pix.domain.vo.StatusChavePix;
import com.dynamis.sep_api.pix.domain.vo.TipoChavePix;
import com.dynamis.sep_api.pix.infrastructure.persistence.ChavePixRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListarChavesPixUseCaseTest {

    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 7, 14, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final String VALOR = "usuario@empresa.com";

    private ChavePixRepository repository;
    private ContaOperacionalEscrowQueryPort contaPort;
    private ListarChavesPixUseCase useCase;

    private final UUID contaEscrowId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(ChavePixRepository.class);
        contaPort = mock(ContaOperacionalEscrowQueryPort.class);
        useCase = new ListarChavesPixUseCase(repository, contaPort);

        when(contaPort.buscarContaOperacionalAtiva())
                .thenReturn(Optional.of(new ContaOperacionalEscrowView(contaEscrowId, "conta-tec-1")));
    }

    private ChavePix chave(String key, OffsetDateTime criadaEm) {
        return ChavePix.cadastrar(
                contaEscrowId,
                TipoChavePix.EMAIL,
                ChavePixSeguranca.hashHex(VALOR),
                ChavePixSeguranca.mascarar(VALOR),
                "prov-" + key,
                key,
                UUID.randomUUID(),
                criadaEm);
    }

    @Test
    void listaAtivaEInativaNaOrdemDoRepositorio() {
        ChavePix recente = chave("idem-2", BASE.plusMinutes(2));
        ChavePix antiga = chave("idem-1", BASE);
        antiga.inativar(UUID.randomUUID(), BASE.plusMinutes(1));
        when(repository.findAllByContaEscrowIdOrderByCriadaEmDesc(contaEscrowId))
                .thenReturn(List.of(recente, antiga));

        List<ChavePixItemResult> resultado = useCase.executar();

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).status()).isEqualTo(StatusChavePix.ATIVA);
        assertThat(resultado.get(0).criadaEm()).isEqualTo(BASE.plusMinutes(2));
        assertThat(resultado.get(1).status()).isEqualTo(StatusChavePix.INATIVA);
        assertThat(resultado.get(1).removidaEm()).isEqualTo(BASE.plusMinutes(1));
    }

    @Test
    void resultCarregaApenasCamposPublicosMascarados() {
        ChavePix chave = chave("idem-1", BASE);
        when(repository.findAllByContaEscrowIdOrderByCriadaEmDesc(contaEscrowId))
                .thenReturn(List.of(chave));

        ChavePixItemResult item = useCase.executar().get(0);

        assertThat(item.id()).isEqualTo(chave.getId());
        assertThat(item.tipo()).isEqualTo(TipoChavePix.EMAIL);
        assertThat(item.valorMascarado()).isEqualTo(chave.getValorMascarado()).isNotEqualTo(VALOR);
        assertThat(item.toString())
                .doesNotContain(VALOR)
                .doesNotContain(chave.getValorHash())
                .doesNotContain(chave.getProviderKeyId())
                .doesNotContain(chave.getIdempotencyKey());
    }

    @Test
    void listaVaziaEhValida() {
        when(repository.findAllByContaEscrowIdOrderByCriadaEmDesc(contaEscrowId))
                .thenReturn(List.of());

        assertThat(useCase.executar()).isEmpty();
    }

    @Test
    void contaOperacionalAusente_retornaListaVaziaSemConsultarRepositorio() {
        when(contaPort.buscarContaOperacionalAtiva()).thenReturn(Optional.empty());

        assertThat(useCase.executar()).isEmpty();
        verify(repository, never()).findAllByContaEscrowIdOrderByCriadaEmDesc(contaEscrowId);
    }
}
