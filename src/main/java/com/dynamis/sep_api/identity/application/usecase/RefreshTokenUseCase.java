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
import com.dynamis.sep_api.usuarios.application.exception.UsuarioNaoEncontradoException;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.mapper.UsuarioMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Rotacao de refresh token com deteccao de reuso (Sprint 5 Task 5.3) + transicao atomica
 * concorrency-safe (Sprint 5 follow-up 5F-FIX-06).
 *
 * <p>Comportamento:
 *
 * <ul>
 *   <li>Token ATIVO e dentro do prazo: a transicao ATIVO -> USADO acontece via update condicional
 *       no banco (uma unica transacao vence em corrida); o vencedor emite novo refresh (mesma
 *       familia) + novo access.
 *   <li>Token USADO re-apresentado (ou perdedor da corrida): revoga toda a familia + grava {@link
 *       TipoEventoSeguranca#REFRESH_REUSE_DETECTED} + lanca 401 (compromisso provavel ou refresh
 *       simultaneo malicioso).
 *   <li>Token REVOGADO / EXPIRADO / desconhecido: lanca 401 sem revogar familia.
 * </ul>
 */
@Service
public class RefreshTokenUseCase {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenUseCase.class);

    private final RefreshTokenRepository repository;
    private final RefreshTokenService refreshTokenService;
    private final UsuarioRepository usuarioRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final UsuarioMapper usuarioMapper;
    private final AuditLogSegurancaRepository auditRepository;

    public RefreshTokenUseCase(
            RefreshTokenRepository repository,
            RefreshTokenService refreshTokenService,
            UsuarioRepository usuarioRepository,
            JwtTokenProvider jwtTokenProvider,
            JwtProperties jwtProperties,
            UsuarioMapper usuarioMapper,
            AuditLogSegurancaRepository auditRepository) {
        this.repository = repository;
        this.refreshTokenService = refreshTokenService;
        this.usuarioRepository = usuarioRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.usuarioMapper = usuarioMapper;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public TokenResponseDto executar(String refreshTokenCru) {
        if (refreshTokenCru == null || refreshTokenCru.isBlank()) {
            throw new BadCredentialsException("Refresh token invalido");
        }
        String hash = refreshTokenService.hashSha256Hex(refreshTokenCru);

        // Transicao atomica ATIVO -> USADO. Apenas a transacao vencedora recebe rows=1.
        int rowsAfetadas = repository.marcarUsadoSeAtivo(hash, OffsetDateTime.now());

        if (rowsAfetadas == 0) {
            // Token nao estava ATIVO. Re-le do banco para classificar o caso:
            // - USADO  -> reuse / corrida concorrente: revoga familia + audita
            // - REVOGADO/EXPIRADO / inexistente -> 401 simples
            Optional<RefreshToken> opt = repository.findByTokenHash(hash);
            if (opt.isPresent() && opt.get().foiUsado()) {
                RefreshToken usado = opt.get();
                log.warn("Reuse detection: refresh token ja usado foi reapresentado");
                repository.revogarFamilia(usado.getFamilyId(), OffsetDateTime.now());
                auditRepository.save(AuditLogSeguranca.registrar(
                        TipoEventoSeguranca.REFRESH_REUSE_DETECTED,
                        usado.getUsuarioId(),
                        null,
                        null,
                        "{\"familyId\":\"" + usado.getFamilyId() + "\",\"acao\":\"revogar-familia\"}"));
            }
            throw new BadCredentialsException("Refresh token invalido");
        }

        // Vencedor: recupera entidade ja USADA para extrair familia/usuario.
        RefreshToken atual = repository
                .findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token invalido"));

        Usuario usuario = usuarioRepository
                .findById(atual.getUsuarioId())
                .orElseThrow(() -> new UsuarioNaoEncontradoException(atual.getUsuarioId()));

        String accessToken = jwtTokenProvider.gerarToken(usuario);
        TokenCru novo = refreshTokenService.rotacionar(usuario.getId(), atual.getFamilyId());

        return new TokenResponseDto(
                accessToken,
                "Bearer",
                jwtProperties.getAccessExpirationSeconds(),
                novo.token(),
                usuarioMapper.toResponse(usuario),
                false,
                null);
    }
}
