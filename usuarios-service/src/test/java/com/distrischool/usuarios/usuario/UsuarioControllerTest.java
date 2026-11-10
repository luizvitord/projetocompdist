package com.distrischool.usuarios.usuario;

import com.distrischool.usuarios.common.RecursoNaoEncontradoException;
import com.distrischool.usuarios.common.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsuarioController.class)
class UsuarioControllerTest {

    private static final String CORPO_VALIDO = """
            {"nome": "Maria Lima", "email": "maria@distrischool.com", "senha": "segredo1",
             "perfil": "PROFESSOR", "referenciaId": 3}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioService usuarioService;

    private static UsuarioResponse resposta() {
        return new UsuarioResponse(1L, "Maria Lima", "maria@distrischool.com", Perfil.PROFESSOR, 3L,
                true, Instant.now(), Instant.now());
    }

    @Test
    void criaUsuarioERetorna201SemExporASenha() throws Exception {
        when(usuarioService.criar(any())).thenReturn(resposta());

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/usuarios/1"))
                .andExpect(jsonPath("$.perfil").value("PROFESSOR"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @Test
    void rejeitaUsuarioComCamposInvalidos() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nao-e-email", "senha": "123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.nome").exists())
                .andExpect(jsonPath("$.erros.email").exists())
                .andExpect(jsonPath("$.erros.senha").exists())
                .andExpect(jsonPath("$.erros.perfil").exists());

        verify(usuarioService, never()).criar(any());
    }

    @Test
    void retorna409QuandoEmailJaExiste() throws Exception {
        when(usuarioService.criar(any()))
                .thenThrow(new RegraNegocioException("Já existe um usuário com o email maria@distrischool.com"));

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    void retorna404QuandoUsuarioNaoExiste() throws Exception {
        when(usuarioService.buscarPorId(99L)).thenThrow(new RecursoNaoEncontradoException("Usuário", 99L));

        mockMvc.perform(get("/api/usuarios/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void desativaUsuarioERetorna204() throws Exception {
        mockMvc.perform(delete("/api/usuarios/1"))
                .andExpect(status().isNoContent());

        verify(usuarioService).desativar(1L);
    }
}
