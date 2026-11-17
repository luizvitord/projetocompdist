package com.distrischool.usuarios.usuario;

import com.distrischool.usuarios.common.RecursoNaoEncontradoException;
import com.distrischool.usuarios.common.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UsuarioResponse criar(UsuarioRequest request) {
        validarVinculo(request.perfil(), request.referenciaId());
        if (usuarioRepository.existsByEmailIgnoreCase(request.email())) {
            throw new RegraNegocioException("Já existe um usuário com o email " + request.email());
        }
        Usuario usuario = new Usuario();
        usuario.setNome(request.nome());
        usuario.setEmail(request.email().trim().toLowerCase());
        usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
        usuario.setPerfil(request.perfil());
        usuario.setReferenciaId(request.referenciaId());
        return UsuarioResponse.from(salvar(usuario));
    }

    @Transactional(readOnly = true)
    public Page<UsuarioResponse> buscar(Perfil perfil, boolean ativo, String busca, Pageable pageable) {
        String termo = busca == null || busca.isBlank() ? null : busca.trim();
        Page<Usuario> usuarios = usuarioRepository.buscar(ativo, perfil, termo, pageable);
        return usuarios.map(UsuarioResponse::from);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        return UsuarioResponse.from(obter(id));
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, AtualizacaoUsuarioRequest request) {
        Usuario usuario = obter(id);
        validarVinculo(request.perfil(), request.referenciaId());
        if (usuarioRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new RegraNegocioException("Já existe um usuário com o email " + request.email());
        }
        String email = request.email().trim().toLowerCase();
        boolean acessoAlterado = !Objects.equals(usuario.getEmail(), email)
                || usuario.getPerfil() != request.perfil()
                || !Objects.equals(usuario.getReferenciaId(), request.referenciaId());
        usuario.setNome(request.nome());
        usuario.setEmail(email);
        usuario.setPerfil(request.perfil());
        usuario.setReferenciaId(request.referenciaId());
        if (acessoAlterado) {
            usuario.invalidarSessoesExistentes();
        }
        return UsuarioResponse.from(salvar(usuario));
    }

    @Transactional
    public void alterarSenha(Long id, AlteracaoSenhaRequest request) {
        Usuario usuario = obter(id);
        usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
        usuario.invalidarSessoesExistentes();
        salvar(usuario);
    }

    /** Exclusão lógica: o usuário deixa de conseguir entrar, mas o registro é mantido. */
    @Transactional
    public void desativar(Long id) {
        Usuario usuario = obter(id);
        if (usuario.isAtivo() && usuario.getPerfil() == Perfil.ADMIN
                && usuarioRepository.countByAtivoTrueAndPerfil(Perfil.ADMIN) <= 1) {
            throw new RegraNegocioException("Não é possível desativar o último administrador ativo");
        }
        if (usuario.isAtivo()) {
            usuario.setAtivo(false);
            usuario.invalidarSessoesExistentes();
        }
    }

    @Transactional(readOnly = true)
    public EstadoSessaoResponse estadoSessao(Long id) {
        Usuario usuario = obter(id);
        return new EstadoSessaoResponse(usuario.isAtivo(), usuario.getVersaoSessao());
    }

    private Usuario obter(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", id));
    }

    /**
     * Força o flush para que uma violação de unicidade causada por cadastros concorrentes
     * (que passaram pela verificação acima) vire 409 em vez de 500.
     */
    private Usuario salvar(Usuario usuario) {
        try {
            return usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException ex) {
            throw new RegraNegocioException("Já existe um usuário com este email");
        }
    }

    private void validarVinculo(Perfil perfil, Long referenciaId) {
        if (perfil == Perfil.ADMIN && referenciaId != null) {
            throw new RegraNegocioException("O perfil ADMIN não pode ter vínculo");
        }
        if (perfil != Perfil.ADMIN && (referenciaId == null || referenciaId <= 0)) {
            throw new RegraNegocioException("Os perfis não administrativos exigem um vínculo válido");
        }
    }
}
