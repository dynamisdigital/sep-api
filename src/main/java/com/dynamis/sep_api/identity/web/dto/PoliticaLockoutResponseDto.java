package com.dynamis.sep_api.identity.web.dto;

import com.dynamis.sep_api.identity.domain.model.PoliticaLockout;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Politica de bloqueio por tentativas, publicada para quem ainda nao tem sessao (Sprint 34 Task
 * 34.5).
 *
 * <p>O {@code Retry-After} do {@code 423} resolve o login, mas nao a pagina de conta bloqueada: ela
 * renderiza sem ter recebido resposta de API — o {@code errorInterceptor} do web navega e descarta o
 * erro —, entao ate a Sprint 33 fixava "30 minutos" no texto.
 *
 * <p>Ate a Sprint 34, {@code lockoutMinutes} tambem saia na {@code message} do {@code 423}; a
 * Sprint 35 Task 35.7 fez aquela frase anunciar o tempo <b>restante</b>, entao os <b>tres</b> valores
 * da politica so sao publicados aqui. Antes disso, os outros dois so eram legiveis no
 * {@code /v3/api-docs} — que e {@code permitAll} e
 * fica habilitado em producao —, onde a descricao do {@code 423} enuncia "5 falhas em 15 min" com os
 * <b>defaults fixos no codigo</b>. O incremento real deste endpoint e refletir o valor <b>efetivo</b>
 * do ambiente em vez do default. Aceito: os numeros sao de baixa entropia, o lockout e por conta
 * (spraying nao o dispara) e a alternativa e o cliente adivinhar.
 *
 * <p>Construido a partir de {@link PoliticaLockout}, o value object que o {@code LockoutService}
 * aplica — e nao da configuracao crua — para que o anuncio nao possa divergir do que e imposto.
 */
@Schema(
        description = "Politica de bloqueio de conta por tentativas de login falhas. Os valores refletem a"
                + " configuracao efetiva do ambiente; os exemplos abaixo sao os defaults.")
public record PoliticaLockoutResponseDto(
        @Schema(description = "Falhas dentro da janela que bloqueiam a conta.", example = "5") int maxAttempts,
        @Schema(description = "Janela de deteccao das falhas, em minutos.", example = "15") int windowMinutes,
        @Schema(description = "Duracao do bloqueio, em minutos, contada do evento que bloqueou.", example = "30")
                int lockoutMinutes) {

    public static PoliticaLockoutResponseDto de(PoliticaLockout politica) {
        return new PoliticaLockoutResponseDto(
                politica.maxAttempts(), (int) politica.janelaDeteccao().toMinutes(), (int)
                        politica.duracaoBloqueio().toMinutes());
    }
}
