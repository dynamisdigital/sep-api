package com.dynamis.sep_api.identity.infrastructure.totp;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Adapter sobre a biblioteca {@code com.warrenstrange:googleauth} (RFC 6238). Encapsula geracao de
 * secret Base32, geracao de URI {@code otpauth://}, geracao de QR code PNG/data-URL e validacao de
 * codigos com janela de tolerancia.
 */
@Component
public class GoogleAuthAdapter {

    private final TotpProperties properties;
    private final GoogleAuthenticator authenticator;

    public GoogleAuthAdapter(TotpProperties properties) {
        this.properties = properties;
        GoogleAuthenticatorConfig config = new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                .setWindowSize(properties.getWindowSize())
                .build();
        this.authenticator = new GoogleAuthenticator(config);
    }

    /** Gera novo secret Base32 com backup codes auxiliares (nao usamos os do googleauth aqui). */
    public String gerarSecretBase32() {
        GoogleAuthenticatorKey key = authenticator.createCredentials();
        return key.getKey();
    }

    /** Monta a URI {@code otpauth://totp/SEP:<email>?secret=<secret>&issuer=SEP} (RFC 6238). */
    public String gerarOtpAuthUri(String email, String secretBase32) {
        String issuer = properties.getIssuer();
        String label = URLEncoder.encode(issuer + ":" + email, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label
                + "?secret=" + secretBase32
                + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    /**
     * Gera o QR code como data URL PNG. A imagem e {@code 200x200} px. Cliente pode renderizar
     * diretamente em {@code <img src="...">}.
     */
    public String gerarQrCodeDataUrl(String otpAuthUri) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(otpAuthUri, BarcodeFormat.QR_CODE, 200, 200);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            String base64 = Base64.getEncoder().encodeToString(out.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao gerar QR code do TOTP", ex);
        }
    }

    /** Valida o codigo de 6 digitos contra o secret Base32. */
    public boolean validarCodigo(String secretBase32, int codigo) {
        return authenticator.authorize(secretBase32, codigo);
    }
}
