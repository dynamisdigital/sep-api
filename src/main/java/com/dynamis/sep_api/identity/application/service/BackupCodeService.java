package com.dynamis.sep_api.identity.application.service;

import com.dynamis.sep_api.identity.domain.model.UsuarioBackupCode;
import com.dynamis.sep_api.identity.infrastructure.persistence.UsuarioBackupCodeRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Geracao, validacao e marcacao de uso dos backup codes do MFA TOTP (Sprint 5 Task 5.2).
 *
 * <p>Politica: 10 codigos de 8 caracteres alfanumericos (sem caracteres ambiguos como 0/O e 1/l).
 * Cada codigo so e exibido em claro uma unica vez (no setup); persistencia apenas como hash
 * BCrypt.
 */
@Service
public class BackupCodeService {

    static final int QUANTIDADE = 10;
    static final int TAMANHO_CODIGO = 8;
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UsuarioBackupCodeRepository repository;
    private final BCryptPasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    public BackupCodeService(UsuarioBackupCodeRepository repository) {
        this.repository = repository;
        this.encoder = new BCryptPasswordEncoder();
    }

    /** Gera 10 codigos claros e persiste as hashes. Retorna a lista clara para exibir uma vez. */
    @Transactional
    public List<String> gerarParaUsuario(UUID usuarioId) {
        repository.deleteByUsuarioId(usuarioId);
        List<String> codigosClaros = new ArrayList<>(QUANTIDADE);
        for (int i = 0; i < QUANTIDADE; i++) {
            String claro = gerarCodigo();
            codigosClaros.add(claro);
            String hash = encoder.encode(claro);
            repository.save(UsuarioBackupCode.criar(usuarioId, hash));
        }
        return codigosClaros;
    }

    /**
     * Tenta consumir um backup code: se algum hash nao usado bater, marca como usado e devolve
     * {@code true}.
     */
    @Transactional
    public boolean consumir(UUID usuarioId, String codigoClaro) {
        if (codigoClaro == null || codigoClaro.isBlank()) {
            return false;
        }
        List<UsuarioBackupCode> disponiveis = repository.findByUsuarioIdAndUsadoFalse(usuarioId);
        for (UsuarioBackupCode candidato : disponiveis) {
            if (encoder.matches(codigoClaro, candidato.getCodigoHash())) {
                candidato.marcarUsado();
                repository.save(candidato);
                return true;
            }
        }
        return false;
    }

    private String gerarCodigo() {
        StringBuilder sb = new StringBuilder(TAMANHO_CODIGO);
        for (int i = 0; i < TAMANHO_CODIGO; i++) {
            sb.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
