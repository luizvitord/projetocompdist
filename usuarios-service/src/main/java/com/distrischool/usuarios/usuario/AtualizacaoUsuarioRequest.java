package com.distrischool.usuarios.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Atualização dos dados do usuário; a senha não muda por aqui. */
public record AtualizacaoUsuarioRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Email @Size(max = 150) String email,
        @NotNull Perfil perfil,
        Long referenciaId
) {
}
