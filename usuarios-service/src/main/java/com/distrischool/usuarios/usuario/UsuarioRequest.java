package com.distrischool.usuarios.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cadastro de usuário. O BCrypt considera só os primeiros 72 bytes da senha. */
public record UsuarioRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 6, max = 72) String senha,
        @NotNull Perfil perfil,
        Long referenciaId
) {
}
