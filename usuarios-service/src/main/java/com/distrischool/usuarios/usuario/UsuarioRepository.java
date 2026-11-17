package com.distrischool.usuarios.usuario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    long countByAtivoTrueAndPerfil(Perfil perfil);

    Page<Usuario> findByAtivo(boolean ativo, Pageable pageable);

    Page<Usuario> findByAtivoAndPerfil(boolean ativo, Perfil perfil, Pageable pageable);

    @Query("""
            select usuario from Usuario usuario
            where usuario.ativo = :ativo
              and (:perfil is null or usuario.perfil = :perfil)
              and (:busca is null
                   or lower(usuario.nome) like lower(concat('%', :busca, '%'))
                   or lower(usuario.email) like lower(concat('%', :busca, '%')))
            """)
    Page<Usuario> buscar(@Param("ativo") boolean ativo, @Param("perfil") Perfil perfil,
                         @Param("busca") String busca, Pageable pageable);
}
