package com.dynamis.sep_api.identity.application;

/**
 * Canal cliente para diferenciar entrega de refresh token (follow-up 5F-FIX-02 da Sprint 5).
 *
 * <ul>
 *   <li>{@link #WEB}: refresh viaja em {@code Set-Cookie} HttpOnly/Secure/SameSite para impedir
 *       leitura por XSS; body de {@code TokenResponseDto} omite o refresh.
 *   <li>{@link #MOBILE}: refresh continua no body para persistencia via {@code
 *       Capacitor Preferences} (cookie nao se aplica em apps nativos).
 * </ul>
 *
 * Default e {@link #MOBILE} quando o header {@code X-Client-Channel} estiver ausente — preserva
 * compatibilidade com clientes antigos que ainda consomem o refresh do body.
 */
public enum ClientChannel {
    WEB,
    MOBILE;

    public static final String HEADER = "X-Client-Channel";

    /** Resolve canal a partir do header HTTP; default {@link #MOBILE} para compat. */
    public static ClientChannel fromHeader(String headerValue) {
        if (headerValue == null) {
            return MOBILE;
        }
        String norm = headerValue.trim().toUpperCase();
        if (norm.equals(WEB.name())) {
            return WEB;
        }
        return MOBILE;
    }
}
