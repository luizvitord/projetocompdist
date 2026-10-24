package com.distrischool.gestao_administrativa.turma;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/turmas")
@RequiredArgsConstructor
public class TurmaController {

    private final TurmaService turmaService;

    @PostMapping
    public ResponseEntity<TurmaResponse> criar(@Valid @RequestBody TurmaRequest request) {
        TurmaResponse criada = turmaService.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(location).body(criada);
    }

    @GetMapping
    public PagedModel<TurmaResponse> buscar(
            @RequestParam(required = false) Integer anoLetivo,
            @RequestParam(required = false) Turno turno,
            @RequestParam(required = false) Long professorId,
            @PageableDefault(size = 20, sort = "codigo", direction = Sort.Direction.ASC) Pageable pageable) {
        return new PagedModel<>(turmaService.buscar(anoLetivo, turno, professorId, pageable));
    }

    @GetMapping("/{id}")
    public TurmaResponse buscarPorId(@PathVariable Long id) {
        return turmaService.buscarPorId(id);
    }

    @PutMapping("/{id}")
    public TurmaResponse atualizar(@PathVariable Long id, @Valid @RequestBody TurmaRequest request) {
        return turmaService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        turmaService.excluir(id);
    }

    @PutMapping("/{id}/professores/{professorId}")
    public TurmaResponse atribuirProfessor(@PathVariable Long id, @PathVariable Long professorId) {
        return turmaService.atribuirProfessor(id, professorId);
    }

    @DeleteMapping("/{id}/professores/{professorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerProfessor(@PathVariable Long id, @PathVariable Long professorId) {
        turmaService.removerProfessor(id, professorId);
    }
}
