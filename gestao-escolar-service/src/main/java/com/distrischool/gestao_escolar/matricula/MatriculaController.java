package com.distrischool.gestao_escolar.matricula;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/** Não há exclusão de matrícula: o encerramento é uma mudança de status (ex.: CANCELADA). */
@RestController
@RequestMapping("/api/matriculas")
@RequiredArgsConstructor
public class MatriculaController {

    private final MatriculaService matriculaService;

    @PostMapping
    public ResponseEntity<MatriculaResponse> criar(@Valid @RequestBody MatriculaRequest request) {
        MatriculaResponse criada = matriculaService.criar(request);
        return ResponseEntity.created(location(criada)).body(criada);
    }

    @GetMapping
    public PagedModel<MatriculaResponse> buscar(
            @RequestParam(required = false) Long alunoId,
            @RequestParam(required = false) Long turmaId,
            @RequestParam(required = false) Integer anoLetivo,
            @RequestParam(required = false) StatusMatricula status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return new PagedModel<>(matriculaService.buscar(alunoId, turmaId, anoLetivo, status, pageable));
    }

    @GetMapping("/{id}")
    public MatriculaResponse buscarPorId(@PathVariable Long id) {
        return matriculaService.buscarPorId(id);
    }

    @PatchMapping("/{id}/status")
    public MatriculaResponse alterarStatus(@PathVariable Long id, @Valid @RequestBody AlteracaoStatusRequest request) {
        return matriculaService.alterarStatus(id, request);
    }

    /** Responde 201 com a nova matrícula (na turma de destino). */
    @PostMapping("/{id}/transferencia")
    public ResponseEntity<MatriculaResponse> transferir(@PathVariable Long id,
                                                        @Valid @RequestBody TransferenciaRequest request) {
        MatriculaResponse nova = matriculaService.transferir(id, request);
        return ResponseEntity.created(location(nova)).body(nova);
    }

    private static URI location(MatriculaResponse matricula) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/matriculas/{id}").buildAndExpand(matricula.id()).toUri();
    }
}
