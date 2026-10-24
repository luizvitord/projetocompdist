package com.distrischool.gestao_escolar.avaliacao;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BoletimController {

    private final BoletimService boletimService;

    @GetMapping("/api/alunos/{alunoId}/boletim")
    public BoletimResponse boletim(@PathVariable Long alunoId, @RequestParam Integer anoLetivo) {
        return boletimService.boletim(alunoId, anoLetivo);
    }
}
