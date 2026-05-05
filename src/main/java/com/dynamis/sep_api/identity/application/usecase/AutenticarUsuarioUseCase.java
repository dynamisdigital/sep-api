package com.dynamis.sep_api.identity.application.usecase;

import com.dynamis.sep_api.identity.infrastructure.security.JwtTokenProvider;
import com.dynamis.sep_api.identity.web.dto.LoginRequestDto;
import com.dynamis.sep_api.identity.web.dto.TokenResponseDto;
import com.dynamis.sep_api.usuarios.domain.model.Usuario;
import com.dynamis.sep_api.usuarios.infrastructure.persistence.UsuarioRepository;
import com.dynamis.sep_api.usuarios.web.mapper.UsuarioMapper;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticarUsuarioUseCase {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final UsuarioMapper mapper;

    public AutenticarUsuarioUseCase(
            UsuarioRepository repository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            UsuarioMapper mapper) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public TokenResponseDto executar(LoginRequestDto dto) {
        Usuario usuario = repository
                .findByUsername(dto.username())
                .orElseThrow(() -> new BadCredentialsException("Credenciais invalidas"));
        if (!passwordEncoder.matches(dto.password(), usuario.getPassword())) {
            throw new BadCredentialsException("Credenciais invalidas");
        }
        String token = tokenProvider.gerarToken(usuario);
        return new TokenResponseDto(token, "Bearer", tokenProvider.getExpirationSeconds(), mapper.toResponse(usuario));
    }
}
