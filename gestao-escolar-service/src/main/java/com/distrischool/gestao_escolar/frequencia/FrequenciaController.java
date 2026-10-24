package com.distrischool.gestao_escolar.frequencia;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/frequencias")
@RequiredArgsConstructor
public class FrequenciaController {

    private final ChamadaService chamadaService;
    private final RelatorioFrequenciaService relatorioService;

    @PostMapping("/chamadas")
    public ResponseEntity<ChamadaResponse> registrar(@Valid @RequestBody ChamadaRequest request) {
        ChamadaResponse criada = chamadaService.registrar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(location).body(criada);
    }

    @GetMapping("/chamadas")
    public PagedModel<ChamadaResponse> buscar(
            @RequestParam(required = false) Long turmaId,
            @RequestParam(required = false) Long disciplinaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @PageableDefault(size = 20, sort = "data", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(chamadaService.buscar(turmaId, disciplinaId, dataInicio, dataFim, pageable));
    }

    @GetMapping("/chamadas/{id}")
    public ChamadaResponse buscarPorId(@PathVariable Long id) {
        return chamadaService.buscarPorId(id);
    }

    @PutMapping("/chamadas/{id}")
    public ChamadaResponse atualizar(@PathVariable Long id, @Valid @RequestBody ChamadaRequest request) {
        return chamadaService.atualizar(id, request);
    }

    @GetMapping("/relatorio")
    public RelatorioFrequenciaResponse relatorio(
            @RequestParam Long turmaId,
            @RequestParam(required = false) Long disciplinaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return relatorioService.relatorio(turmaId, disciplinaId, dataInicio, dataFim);
    }

    @GetMapping("/alunos/{alunoId}")
    public FrequenciaAlunoResponse porAluno(@PathVariable Long alunoId, @RequestParam Integer anoLetivo) {
        return relatorioService.porAluno(alunoId, anoLetivo);
    }
}
