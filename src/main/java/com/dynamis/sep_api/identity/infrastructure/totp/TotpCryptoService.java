package com.dynamis.sep_api.identity.infrastructure.totp;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifra e decifra o secret TOTP em repouso usando AES-256/GCM. A chave vem de {@code
 * app.security.totp.encryption-key} derivada via SHA-256 para garantir 32 bytes.
 *
 * <p>Formato persistido: Base64({@code iv} || {@code ciphertext+tag}) — IV gerado por chamada (12
 * bytes), tag GCM de 128 bits anexada ao ciphertext pelo proprio {@code Cipher}.
 */
@Component
public class TotpCryptoService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TotpCryptoService(TotpProperties properties) {
        if (properties.getEncryptionKey() == null
                || properties.getEncryptionKey().isBlank()) {
            throw new IllegalStateException("Propriedade app.security.totp.encryption-key obrigatoria.");
        }
        this.key = derivarChave(properties.getEncryptionKey());
    }

    public String cifrar(String secretClaro) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(secretClaro.getBytes(StandardCharsets.UTF_8));
            byte[] combinado = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combinado, 0, iv.length);
            System.arraycopy(ciphertext, 0, combinado, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combinado);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao cifrar secret TOTP", ex);
        }
    }

    public String decifrar(String cifradoBase64) {
        try {
            byte[] combinado = Base64.getDecoder().decode(cifradoBase64);
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combinado.length - IV_LENGTH];
            System.arraycopy(combinado, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combinado, IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] claro = cipher.doFinal(ciphertext);
            return new String(claro, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao decifrar secret TOTP", ex);
        }
    }

    private SecretKeySpec derivarChave(String segredo) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(segredo.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, ALGORITHM);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 nao disponivel", ex);
        }
    }
}
