package com.dynamis.sep_api.identity.web.dto;

import com.dynamis.sep_api.identity.infrastructure.security.LockoutProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Politica de bloqueio por tentativas, publicada para quem ainda nao tem sessao (Sprint 34 Task
 * 34.5).
 *
 * <p>O {@code Retry-After} do {@code 423} resolve o login, mas nao a pagina de conta bloqueada: ela
 * renderiza sem ter recebido resposta de API — o {@code errorInterceptor} do web navega e descarta o
 * erro —, entao ate a Sprint 33 fixava "30 minutos" no texto. Estes numeros ja eram publicos pela
 * {@code message} do {@code 423}; o endpoint so os torna legiveis sem provocar um bloqueio.
 *
 * <p>Nao confundir com {@code PoliticaLockout}, o value object de dominio que <b>decide</b> o
 * bloqueio. Este record apenas transporta a configuracao.
 */
@Schema(description = "Politica de bloqueio de conta por tentativas de login falhas.")
public record PoliticaLockoutResponseDto(
        @Schema(description = "Falhas dentro da janela que bloqueiam a conta.", example = "5") int maxAttempts,
        @Schema(description = "Janela de deteccao das falhas, em minutos.", example = "15") int windowMinutes,
        @Schema(description = "Duracao do bloqueio, em minutos, contada do evento que bloqueou.", example = "30")
                int lockoutMinutes) {

    public static PoliticaLockoutResponseDto de(LockoutProperties properties) {
        return new PoliticaLockoutResponseDto(
                properties.getMaxAttempts(), properties.getWindowMinutes(), properties.getLockoutMinutes());
    }
}
