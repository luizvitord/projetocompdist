package com.distrischool.usuarios.auth;

import com.distrischool.usuarios.usuario.Usuario;
import com.distrischool.usuarios.usuario.UsuarioRepository;
import com.distrischool.usuarios.usuario.UsuarioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(Usuario::isAtivo)
                .filter(u -> passwordEncoder.matches(request.senha(), u.getSenhaHash()))
                .orElseThrow(CredenciaisInvalidasException::new);

        TokenService.TokenGerado token = tokenService.gerar(usuario);
        return new LoginResponse(token.token(), token.expiraEm(), UsuarioResponse.from(usuario));
    }
}
