package com.distrischool.gestao_escolar.avaliacao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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

import java.util.List;

@RestController
@RequestMapping("/api/avaliacoes")
@RequiredArgsConstructor
public class AvaliacaoController {

    private final AvaliacaoService avaliacaoService;
    private final NotaService notaService;

    @PostMapping
    public ResponseEntity<AvaliacaoResponse> criar(@Valid @RequestBody AvaliacaoRequest request) {
        AvaliacaoResponse criada = avaliacaoService.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(location).body(criada);
    }

    @GetMapping
    public PagedModel<AvaliacaoResponse> buscar(
            @RequestParam(required = false) Long turmaId,
            @RequestParam(required = false) Long disciplinaId,
            @RequestParam(required = false) Integer bimestre,
            @PageableDefault(size = 20, sort = "data", direction = Sort.Direction.ASC) Pageable pageable) {
        return new PagedModel<>(avaliacaoService.buscar(turmaId, disciplinaId, bimestre, pageable));
    }

    @GetMapping("/{id}")
    public AvaliacaoResponse buscarPorId(@PathVariable Long id) {
        return avaliacaoService.buscarPorId(id);
    }

    @PutMapping("/{id}")
    public AvaliacaoResponse atualizar(@PathVariable Long id, @Valid @RequestBody AvaliacaoRequest request) {
        return avaliacaoService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        avaliacaoService.excluir(id);
    }

    /** Lançamento em lote; a correção de uma nota já lançada é em {@code PUT /api/notas/{id}}. */
    @PostMapping("/{id}/notas")
    @ResponseStatus(HttpStatus.CREATED)
    public List<NotaResponse> lancarNotas(@PathVariable Long id,
                                          @RequestBody @NotEmpty List<@Valid LancamentoNotaRequest> notas) {
        return notaService.lancar(id, notas);
    }

    @GetMapping("/{id}/notas")
    public List<NotaResponse> listarNotas(@PathVariable Long id) {
        return notaService.listar(id);
    }
}
