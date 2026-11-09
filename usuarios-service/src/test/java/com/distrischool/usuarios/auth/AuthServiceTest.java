package com.distrischool.usuarios.auth;

import com.distrischool.usuarios.usuario.Perfil;
import com.distrischool.usuarios.usuario.Usuario;
import com.distrischool.usuarios.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Login; sem Spring, com repositório e gerador de token simulados. */
class AuthServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UsuarioRepository usuarioRepository;
    private TokenService tokenService;
    private AuthService authService;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        usuarioRepository = mock(UsuarioRepository.class);
        tokenService = mock(TokenService.class);
        authService = new AuthService(usuarioRepository, passwordEncoder, tokenService);

        usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNome("Coordenação");
        usuario.setEmail("coordenacao@distrischool.com");
        usuario.setSenhaHash(passwordEncoder.encode("123456"));
        usuario.setPerfil(Perfil.ADMIN);
        when(usuarioRepository.findByEmailIgnoreCase("coordenacao@distrischool.com")).thenReturn(Optional.of(usuario));
        when(tokenService.gerar(any())).thenReturn(new TokenService.TokenGerado("token", Instant.EPOCH));
    }

    @Test
    void entregaTokenEDadosDoUsuarioComSenhaCorreta() {
        LoginResponse resposta = authService.login(new LoginRequest("coordenacao@distrischool.com", "123456"));

        assertThat(resposta.token()).isEqualTo("token");
        assertThat(resposta.usuario().perfil()).isEqualTo(Perfil.ADMIN);
    }

    @Test
    void recusaSenhaErrada() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("coordenacao@distrischool.com", "errada")))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verify(tokenService, never()).gerar(any());
    }

    @Test
    void recusaEmailInexistente() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("ninguem@distrischool.com", "123456")))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void recusaUsuarioDesativado() {
        usuario.setAtivo(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("coordenacao@distrischool.com", "123456")))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }
}
