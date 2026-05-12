package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.application.service.RefreshTokenService;
import com.dynamis.sep_api.identity.application.service.RefreshTokenService.TokenCru;
import com.dynamis.sep_api.identity.domain.model.RefreshToken;
import com.dynamis.sep_api.identity.infrastructure.persistence.RefreshTokenRepository;
import com.dynamis.sep_api.identity.infrastructure.security.JwtProperties;
import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.web.dto.TokenResponseDto;
import com.dynamis.sep_api.shared.audit.AuditLogSeguranca;
import com.dynamis.sep_api.shared.audit.AuditLogSegurancaRepository;
import com.dynamis.sep_api.shared.audit.TipoEventoSeguranca;
import com.dynamis.sep_api.usuarios.domain.model.Role;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;
import com.dynamis.sep_api.usuarios.web.mapper.UsuarioMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenUseCaseTest {

    private RefreshTokenRepository repository;
    private RefreshTokenService refreshTokenService;
    private UsuarioRepository usuarioRepository;
    private JwtTokenProvider jwtTokenProvider;
    private JwtProperties jwtProperties;
    private UsuarioMapper usuarioMapper;
    private AuditLogSegurancaRepository auditRepository;
    private RefreshTokenUseCase useCase;

    @BeforeEach
    void setup() {
        repository = mock(RefreshTokenRepository.class);
        refreshTokenService = mock(RefreshTokenService.class);
        usuarioRepository = mock(UsuarioRepository.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        jwtProperties = mock(JwtProperties.class);
        usuarioMapper = mock(UsuarioMapper.class);
        auditRepository = mock(AuditLogSegurancaRepository.class);
        useCase = new RefreshTokenUseCase(
                repository,
                refreshTokenService,
                usuarioRepository,
                jwtTokenProvider,
                jwtProperties,
                usuarioMapper,
                auditRepository);
    }

    private RefreshToken ativo(UUID usuarioId, String hash) {
        return RefreshToken.emitirNovoLogin(
                usuarioId, hash, OffsetDateTime.now().plusDays(30));
    }

    @Test
    void rotacaoComTokenAtivoEmiteNovoParEMarcaAnteriorUsado() {
        UUID usuarioId = UUID.randomUUID();
        RefreshToken atual = ativo(usuarioId, "hash-velho");
        // Simula transicao atomica vencedora: 1 linha afetada.
        when(refreshTokenService.hashSha256Hex("cru-velho")).thenReturn("hash-velho");
        when(repository.marcarUsadoSeAtivo(eq("hash-velho"), any())).thenAnswer(invocation -> {
            atual.marcarUsado();
            return 1;
        });
        when(repository.findByTokenHash("hash-velho")).thenReturn(Optional.of(atual));
        Usuario usuario = Usuario.criar("u@sep.test", "h", Role.CLIENTE);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(jwtTokenProvider.gerarToken(usuario)).thenReturn("novo-access");
        when(jwtProperties.getAccessExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.rotacionar(any(), eq(atual.getFamilyId())))
                .thenReturn(new TokenCru(
                        "novo-cru",
                        RefreshToken.emitir(
                                usuario.getId(),
                                atual.getFamilyId(),
                                "hash-novo",
                                OffsetDateTime.now().plusDays(30))));
        UsuarioResponseDto dto = new UsuarioResponseDto(
                usuario.getId(),
                "u@sep.test",
                Role.CLIENTE,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "system",
                "system",
                false,
                false);
        when(usuarioMapper.toResponse(usuario)).thenReturn(dto);

        TokenResponseDto resp = useCase.executar("cru-velho");

        assertThat(resp.accessToken()).isEqualTo("novo-access");
        assertThat(resp.refreshToken()).isEqualTo("novo-cru");
        assertThat(atual.foiUsado()).isTrue();
        verify(repository).marcarUsadoSeAtivo(eq("hash-velho"), any());
    }

    @Test
    void reuseDetectionRevogaFamiliaEGravaAudit() {
        UUID usuarioId = UUID.randomUUID();
        RefreshToken jaUsado = ativo(usuarioId, "hash-reuso");
        jaUsado.marcarUsado();
        when(refreshTokenService.hashSha256Hex("cru-reuso")).thenReturn("hash-reuso");
        when(repository.marcarUsadoSeAtivo(eq("hash-reuso"), any())).thenReturn(0);
        when(repository.findByTokenHash("hash-reuso")).thenReturn(Optional.of(jaUsado));

        assertThatThrownBy(() -> useCase.executar("cru-reuso")).isInstanceOf(BadCredentialsException.class);

        verify(repository).revogarFamilia(eq(jaUsado.getFamilyId()), any());
        ArgumentCaptor<AuditLogSeguranca> captor = ArgumentCaptor.forClass(AuditLogSeguranca.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoEventoSeguranca.REFRESH_REUSE_DETECTED);
    }

    @Test
    void corridaConcorrentePerdedorEntraEmReuseDetection() {
        // 5F-FIX-06: duas chamadas com mesmo refresh; a segunda recebe rows=0 e cai no caminho
        // de reuse detection, pois a primeira ja transicionou ATIVO -> USADO.
        UUID usuarioId = UUID.randomUUID();
        RefreshToken vencedor = ativo(usuarioId, "hash-corrida");
        vencedor.marcarUsado(); // simula estado pos-corrida da perspectiva do perdedor
        when(refreshTokenService.hashSha256Hex("cru-corrida")).thenReturn("hash-corrida");
        when(repository.marcarUsadoSeAtivo(eq("hash-corrida"), any())).thenReturn(0);
        when(repository.findByTokenHash("hash-corrida")).thenReturn(Optional.of(vencedor));

        assertThatThrownBy(() -> useCase.executar("cru-corrida")).isInstanceOf(BadCredentialsException.class);

        verify(repository).revogarFamilia(eq(vencedor.getFamilyId()), any());
        verify(auditRepository).save(any());
        verify(refreshTokenService, never()).rotacionar(any(), any());
    }

    @Test
    void tokenDesconhecidoLanca401SemRevogarFamilia() {
        when(refreshTokenService.hashSha256Hex(any())).thenReturn("hash-x");
        when(repository.marcarUsadoSeAtivo(eq("hash-x"), any())).thenReturn(0);
        when(repository.findByTokenHash("hash-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar("desconhecido")).isInstanceOf(BadCredentialsException.class);
        verify(repository, never()).revogarFamilia(any(), any());
        verify(auditRepository, never()).save(any());
    }

    @Test
    void tokenExpiradoLanca401SemRevogarFamilia() {
        UUID usuarioId = UUID.randomUUID();
        RefreshToken expirado = RefreshToken.emitirNovoLogin(
                usuarioId, "h", OffsetDateTime.now().minusMinutes(1));
        // Token expirado nao sera atualizado pelo update condicional (status ja deveria ter sido
        // marcado EXPIRADO; se ainda ATIVO no banco, expira_em <= now nao impede o UPDATE — mas o
        // teste do use case mocka o repo, entao retornamos rows=0 explicitamente).
        when(refreshTokenService.hashSha256Hex("cru")).thenReturn("h");
        when(repository.marcarUsadoSeAtivo(eq("h"), any())).thenReturn(0);
        when(repository.findByTokenHash("h")).thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> useCase.executar("cru")).isInstanceOf(BadCredentialsException.class);
        verify(repository, never()).revogarFamilia(any(), any());
        verify(auditRepository, never()).save(any());
    }

    @Test
    void tokenVazioLanca401() {
        assertThatThrownBy(() -> useCase.executar("")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> useCase.executar(null)).isInstanceOf(BadCredentialsException.class);
        verify(repository, never()).marcarUsadoSeAtivo(any(), any());
    }
}
