package com.distrischool.gestao_administrativa.professor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfessorRequest(
        @NotBlank @Size(max = 150) String nome,
        @Size(max = 255) String qualificacao,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 30) String telefone
) {
}
