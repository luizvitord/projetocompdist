package com.distrischool.usuarios.auth;

import com.distrischool.usuarios.usuario.Perfil;
import com.distrischool.usuarios.usuario.UsuarioResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void loginValidoRetornaToken() throws Exception {
        UsuarioResponse usuario = new UsuarioResponse(1L, "Coordenação", "coordenacao@distrischool.com",
                Perfil.ADMIN, null, true, Instant.now(), Instant.now());
        when(authService.login(any()))
                .thenReturn(new LoginResponse("abc.def.ghi", Instant.parse("2026-03-10T20:00:00Z"), usuario));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "coordenacao@distrischool.com", "senha": "123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("abc.def.ghi"))
                .andExpect(jsonPath("$.expiraEm").value("2026-03-10T20:00:00Z"))
                .andExpect(jsonPath("$.usuario.perfil").value("ADMIN"));
    }

    @Test
    void credenciaisInvalidasRetornam401() throws Exception {
        when(authService.login(any())).thenThrow(new CredenciaisInvalidasException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "coordenacao@distrischool.com", "senha": "errada"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email ou senha inválidos"));
    }

    @Test
    void camposObrigatoriosRetornam400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.email").exists())
                .andExpect(jsonPath("$.erros.senha").exists());
    }
}
