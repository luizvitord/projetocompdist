package com.distrischool.gestao_administrativa.tecnico;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;

import java.time.LocalDate;

public record TecnicoRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @CPF String cpf,
        @NotNull Cargo cargo,
        @NotBlank @Size(max = 100) String setor,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 30) String telefone,
        @NotNull @PastOrPresent LocalDate dataAdmissao
) {
}
