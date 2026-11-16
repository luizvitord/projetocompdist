package com.distrischool.usuarios.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Redefinição administrativa de senha; o BCrypt considera apenas os primeiros 72 bytes. */
public record AlteracaoSenhaRequest(
        @NotBlank @Size(min = 6, max = 72) String senha
) {
}
