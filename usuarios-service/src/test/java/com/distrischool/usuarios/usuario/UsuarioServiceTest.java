package com.distrischool.usuarios.usuario;

import com.distrischool.usuarios.common.RegraNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cadastro de usuários; sem Spring, com repositório simulado. */
class UsuarioServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UsuarioRepository usuarioRepository;
    private UsuarioService usuarioService;

    @BeforeEach
    void preparar() {
        usuarioRepository = mock(UsuarioRepository.class);
        usuarioService = new UsuarioService(usuarioRepository, passwordEncoder);

        when(usuarioRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
    }

    @Test
    void guardaSomenteOHashDaSenhaEOEmailEmMinusculas() {
        usuarioService.criar(new UsuarioRequest("Maria", " Maria@DistriSchool.com ", "segredo1", Perfil.PROFESSOR, 3L));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).saveAndFlush(captor.capture());
        Usuario salvo = captor.getValue();
        assertThat(salvo.getEmail()).isEqualTo("maria@distrischool.com");
        assertThat(salvo.getSenhaHash()).isNotEqualTo("segredo1");
        assertThat(passwordEncoder.matches("segredo1", salvo.getSenhaHash())).isTrue();
        assertThat(salvo.isAtivo()).isTrue();
    }

    @Test
    void recusaEmailRepetido() {
        when(usuarioRepository.existsByEmailIgnoreCase("maria@distrischool.com")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.criar(
                new UsuarioRequest("Maria", "maria@distrischool.com", "segredo1", Perfil.ALUNO, 1L)))
                .isInstanceOf(RegraNegocioException.class);
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void recusaVinculoIncompativelComPerfil() {
        assertThatThrownBy(() -> usuarioService.criar(
                new UsuarioRequest("Coordenação", "coord@distrischool.com", "segredo1", Perfil.ADMIN, 4L)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("ADMIN");

        assertThatThrownBy(() -> usuarioService.criar(
                new UsuarioRequest("João", "joao@distrischool.com", "segredo1", Perfil.ALUNO, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("vínculo");

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void desativarMantemORegistro() {
        Usuario usuario = new Usuario();
        usuario.setId(5L);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));

        usuarioService.desativar(5L);

        assertThat(usuario.isAtivo()).isFalse();
        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    void recusaDesativarOUltimoAdministradorAtivo() {
        Usuario usuario = new Usuario();
        usuario.setId(5L);
        usuario.setPerfil(Perfil.ADMIN);
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.countByAtivoTrueAndPerfil(Perfil.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> usuarioService.desativar(5L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("último administrador");
        assertThat(usuario.isAtivo()).isTrue();
    }

    @Test
    void trocaSenhaInvalidaSessoesAnteriores() {
        Usuario usuario = new Usuario();
        usuario.setId(5L);
        usuario.setSenhaHash(passwordEncoder.encode("senhaanterior"));
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));

        usuarioService.alterarSenha(5L, new AlteracaoSenhaRequest("novosegredo"));

        assertThat(passwordEncoder.matches("novosegredo", usuario.getSenhaHash())).isTrue();
        assertThat(usuario.getVersaoSessao()).isEqualTo(2L);
        verify(usuarioRepository).saveAndFlush(usuario);
    }

    @Test
    void retornaEstadoAtualDaSessao() {
        Usuario usuario = new Usuario();
        usuario.setId(5L);
        usuario.setAtivo(false);
        usuario.invalidarSessoesExistentes();
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));

        EstadoSessaoResponse estado = usuarioService.estadoSessao(5L);

        assertThat(estado.ativo()).isFalse();
        assertThat(estado.versaoSessao()).isEqualTo(2L);
    }
}
