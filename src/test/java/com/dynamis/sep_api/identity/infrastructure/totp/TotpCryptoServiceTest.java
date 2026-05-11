package com.dynamis.sep_api.identity.infrastructure.totp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpCryptoServiceTest {

    private TotpCryptoService criar(String key) {
        TotpProperties props = new TotpProperties();
        props.setEncryptionKey(key);
        return new TotpCryptoService(props);
    }

    @Test
    void cifrarEDecifrarRetornaSegredoOriginal() {
        TotpCryptoService svc = criar("chave-teste-com-32-bytes-minimo-x");

        String cifrado = svc.cifrar("JBSWY3DPEHPK3PXP");
        String claro = svc.decifrar(cifrado);

        assertThat(claro).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(cifrado).isNotEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void cadaCifragemUsaIvDiferenteEntaoOutputMudaSempre() {
        TotpCryptoService svc = criar("chave-teste-com-32-bytes-minimo-x");

        String c1 = svc.cifrar("MEU-SECRET");
        String c2 = svc.cifrar("MEU-SECRET");

        assertThat(c1).isNotEqualTo(c2);
    }

    @Test
    void chaveVaziaFalhaConstrucao() {
        TotpProperties props = new TotpProperties();
        props.setEncryptionKey("");

        assertThatThrownBy(() -> new TotpCryptoService(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encryption-key");
    }

    @Test
    void decifrarComChaveDiferenteFalha() {
        TotpCryptoService a = criar("chave-A-com-tamanho-suficiente-x");
        TotpCryptoService b = criar("chave-B-diferente-mas-tambem-ok-x");

        String cifrado = a.cifrar("SECRET");

        assertThatThrownBy(() -> b.decifrar(cifrado)).isInstanceOf(IllegalStateException.class);
    }
}
