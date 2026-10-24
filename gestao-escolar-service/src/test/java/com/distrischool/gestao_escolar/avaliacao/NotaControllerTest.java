package com.distrischool.gestao_escolar.avaliacao;

import com.distrischool.gestao_escolar.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_escolar.common.ValidacaoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotaController.class)
class NotaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotaService notaService;

    @Test
    void corrigeNota() throws Exception {
        when(notaService.corrigir(100L, new CorrecaoNotaRequest(new BigDecimal("7.0"), "Erro de soma")))
                .thenReturn(new NotaResponse(100L, 9L, 4L, new BigDecimal("7.00"), Instant.now(), Instant.now()));

        mockMvc.perform(put("/api/notas/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\": 7.0, \"motivo\": \"Erro de soma\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(7.00));
    }

    @Test
    void correcaoExigeMotivo() throws Exception {
        mockMvc.perform(put("/api/notas/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\": 7.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.motivo").exists());

        verify(notaService, never()).corrigir(any(), any());
    }

    @Test
    void correcaoAcimaDaNotaMaximaRetorna400() throws Exception {
        when(notaService.corrigir(eq(100L), any()))
                .thenThrow(ValidacaoException.campo("valor", "deve ser no máximo 10.00 (nota máxima da avaliação)"));

        mockMvc.perform(put("/api/notas/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\": 11, \"motivo\": \"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.valor").exists());
    }

    @Test
    void historicoDaNota() throws Exception {
        when(notaService.historico(100L)).thenReturn(List.of(new NotaHistoricoResponse(
                1L, 100L, new BigDecimal("6.00"), new BigDecimal("7.00"), "Erro de soma", Instant.now())));

        mockMvc.perform(get("/api/notas/100/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].valorAnterior").value(6.00))
                .andExpect(jsonPath("$[0].valorNovo").value(7.00))
                .andExpect(jsonPath("$[0].motivo").value("Erro de soma"));
    }

    @Test
    void historicoDeNotaInexistenteRetorna404() throws Exception {
        when(notaService.historico(5L)).thenThrow(new RecursoNaoEncontradoException("Nota", 5L));

        mockMvc.perform(get("/api/notas/5/historico"))
                .andExpect(status().isNotFound());
    }
}
