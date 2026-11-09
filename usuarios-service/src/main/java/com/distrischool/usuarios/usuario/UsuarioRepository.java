package com.distrischool.usuarios.usuario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    Page<Usuario> findByAtivo(boolean ativo, Pageable pageable);

    Page<Usuario> findByAtivoAndPerfil(boolean ativo, Perfil perfil, Pageable pageable);
}
