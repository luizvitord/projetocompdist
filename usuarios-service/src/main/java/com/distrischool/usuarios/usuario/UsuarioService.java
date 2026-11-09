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

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UsuarioResponse criar(UsuarioRequest request) {
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
    public Page<UsuarioResponse> buscar(Perfil perfil, boolean ativo, Pageable pageable) {
        Page<Usuario> usuarios = perfil == null
                ? usuarioRepository.findByAtivo(ativo, pageable)
                : usuarioRepository.findByAtivoAndPerfil(ativo, perfil, pageable);
        return usuarios.map(UsuarioResponse::from);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        return UsuarioResponse.from(obter(id));
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, AtualizacaoUsuarioRequest request) {
        Usuario usuario = obter(id);
        if (usuarioRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new RegraNegocioException("Já existe um usuário com o email " + request.email());
        }
        usuario.setNome(request.nome());
        usuario.setEmail(request.email().trim().toLowerCase());
        usuario.setPerfil(request.perfil());
        usuario.setReferenciaId(request.referenciaId());
        return UsuarioResponse.from(salvar(usuario));
    }

    /** Exclusão lógica: o usuário deixa de conseguir entrar, mas o registro é mantido. */
    @Transactional
    public void desativar(Long id) {
        obter(id).setAtivo(false);
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
}
