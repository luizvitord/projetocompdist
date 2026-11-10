package com.distrischool.usuarios.auth;

import com.distrischool.usuarios.usuario.UsuarioResponse;

import java.time.Instant;

/** O frontend guarda o token e o envia no cabeçalho {@code Authorization: Bearer <token>}. */
public record LoginResponse(
        String token,
        Instant expiraEm,
        UsuarioResponse usuario
) {
}
