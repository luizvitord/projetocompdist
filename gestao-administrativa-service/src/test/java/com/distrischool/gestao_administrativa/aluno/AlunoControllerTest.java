package com.distrischool.gestao_administrativa.aluno;

import com.distrischool.gestao_administrativa.common.RecursoNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlunoController.class)
class AlunoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlunoService alunoService;

    @Test
    void criaAlunoERetorna201ComLocation() throws Exception {
        when(alunoService.criar(any())).thenReturn(new AlunoResponse(
                1L, "2026000001", "Maria Silva", LocalDate.of(2012, 3, 10),
                "Rua A, 10", "11999990000", "maria@example.com", Instant.now(), Instant.now()));

        mockMvc.perform(post("/api/alunos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Maria Silva", "dataNascimento": "2012-03-10",
                                 "endereco": "Rua A, 10", "telefone": "11999990000",
                                 "email": "maria@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/alunos/1"))
                .andExpect(jsonPath("$.matricula").value("2026000001"));
    }

    @Test
    void rejeitaAlunoSemNome() throws Exception {
        mockMvc.perform(post("/api/alunos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dataNascimento": "2012-03-10"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.nome").exists());

        verify(alunoService, never()).criar(any());
    }

    @Test
    void retorna404QuandoAlunoNaoExiste() throws Exception {
        when(alunoService.buscarPorId(99L)).thenThrow(new RecursoNaoEncontradoException("Aluno", 99L));

        mockMvc.perform(get("/api/alunos/99"))
                .andExpect(status().isNotFound());
    }
}
