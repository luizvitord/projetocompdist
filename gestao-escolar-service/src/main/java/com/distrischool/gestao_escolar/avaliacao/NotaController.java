package com.distrischool.gestao_escolar.avaliacao;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notas")
@RequiredArgsConstructor
public class NotaController {

    private final NotaService notaService;

    @PutMapping("/{id}")
    public NotaResponse corrigir(@PathVariable Long id, @Valid @RequestBody CorrecaoNotaRequest request) {
        return notaService.corrigir(id, request);
    }

    @GetMapping("/{id}/historico")
    public List<NotaHistoricoResponse> historico(@PathVariable Long id) {
        return notaService.historico(id);
    }
}
