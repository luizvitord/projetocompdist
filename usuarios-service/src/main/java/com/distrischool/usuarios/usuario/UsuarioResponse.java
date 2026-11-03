package com.distrischool.usuarios.usuario;

import java.time.Instant;

/** Não expõe o hash da senha. */
public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        Perfil perfil,
        Long referenciaId,
        boolean ativo,
        Instant createdAt,
        Instant updatedAt
) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil(),
                usuario.getReferenciaId(),
                usuario.isAtivo(),
                usuario.getCreatedAt(),
                usuario.getUpdatedAt()
        );
    }
}
