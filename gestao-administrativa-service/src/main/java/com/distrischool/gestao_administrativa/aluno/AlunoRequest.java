package com.distrischool.gestao_administrativa.aluno;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AlunoRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotNull @Past LocalDate dataNascimento,
        @Size(max = 255) String endereco,
        @Size(max = 30) String telefone,
        @Email @Size(max = 150) String email
) {
}
