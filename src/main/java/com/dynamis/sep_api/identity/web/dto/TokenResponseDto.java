package com.dynamis.sep_api.identity.web.dto;

import com.dynamis.sep_api.usuarios.web.dto.UsuarioResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Resposta de autenticacao. Quando MFA esta ATIVO, login devolve apenas mfaChallengeId.")
public record TokenResponseDto(
        @Schema(
                        description = "JWT access token (15 min). null quando mfaRequired=true.",
                        example = "eyJhbGciOiJIUzI1NiJ9...")
                String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Tempo em segundos ate expirar o accessToken.", example = "900") long expiresIn,
        @Schema(description = "Refresh token cru (Base64URL). Exibido uma unica vez; persistencia apenas como hash.")
                String refreshToken,
        UsuarioResponseDto usuario,
        @Schema(description = "true quando o usuario precisa apresentar codigo TOTP em /auth/totp/verify.")
                boolean mfaRequired,
        @Schema(description = "Challenge a ser apresentado em /auth/totp/verify quando mfaRequired=true.")
                UUID mfaChallengeId) {

    /** Resposta de login bem-sucedido sem MFA exigido. */
    public static TokenResponseDto comTokens(
            String accessToken, long expiresIn, String refreshToken, UsuarioResponseDto usuario) {
        return new TokenResponseDto(accessToken, "Bearer", expiresIn, refreshToken, usuario, false, null);
    }

    /** Resposta de login que ainda exige verificacao TOTP. */
    public static TokenResponseDto desafioMfa(UUID challengeId) {
        return new TokenResponseDto(null, "Bearer", 0, null, null, true, challengeId);
    }
}
