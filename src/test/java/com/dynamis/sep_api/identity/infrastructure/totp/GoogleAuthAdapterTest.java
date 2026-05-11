package com.dynamis.sep_api.identity.infrastructure.totp;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleAuthAdapterTest {

    private GoogleAuthAdapter criar() {
        TotpProperties props = new TotpProperties();
        props.setIssuer("SEP");
        props.setWindowSize(1);
        return new GoogleAuthAdapter(props);
    }

    @Test
    void gerarSecretBase32RetornaStringNaoVazia() {
        GoogleAuthAdapter adapter = criar();

        String secret = adapter.gerarSecretBase32();

        assertThat(secret).isNotBlank();
    }

    @Test
    void otpAuthUriIncluiIssuerEmail() {
        GoogleAuthAdapter adapter = criar();

        String uri = adapter.gerarOtpAuthUri("admin@sep.test", "JBSWY3DPEHPK3PXP");

        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("SEP");
        assertThat(uri).contains("admin%40sep.test");
        assertThat(uri).contains("secret=JBSWY3DPEHPK3PXP");
        assertThat(uri).contains("issuer=SEP");
    }

    @Test
    void qrCodeDataUrlEhPngBase64() {
        GoogleAuthAdapter adapter = criar();
        String uri = adapter.gerarOtpAuthUri("u@sep.test", adapter.gerarSecretBase32());

        String dataUrl = adapter.gerarQrCodeDataUrl(uri);

        assertThat(dataUrl).startsWith("data:image/png;base64,");
        assertThat(dataUrl.length()).isGreaterThan(200);
    }

    @Test
    void validarCodigoAceitaCodigoGeradoPelaMesmaBiblioteca() {
        GoogleAuthAdapter adapter = criar();
        String secret = adapter.gerarSecretBase32();

        GoogleAuthenticator gerador =
                new GoogleAuthenticator(new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder().build());
        int codigo = gerador.getTotpPassword(secret);

        assertThat(adapter.validarCodigo(secret, codigo)).isTrue();
    }

    @Test
    void validarCodigoRejeitaCodigoArbitrario() {
        GoogleAuthAdapter adapter = criar();
        String secret = adapter.gerarSecretBase32();

        assertThat(adapter.validarCodigo(secret, 0)).isFalse();
    }
}
