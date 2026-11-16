package com.distrischool.gestao_administrativa.disciplina;

import com.distrischool.gestao_administrativa.common.BuscaTexto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/disciplinas")
@RequiredArgsConstructor
public class DisciplinaController {

    private final DisciplinaService disciplinaService;

    @PostMapping
    public ResponseEntity<DisciplinaResponse> criar(@Valid @RequestBody DisciplinaRequest request) {
        DisciplinaResponse criada = disciplinaService.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(location).body(criada);
    }

    @GetMapping
    public PagedModel<DisciplinaResponse> buscar(
            @RequestParam(required = false) String nome,
            @RequestParam(defaultValue = "true") boolean ativa,
            @RequestParam(required = false)
            @Size(max = BuscaTexto.TAMANHO_MAXIMO, message = BuscaTexto.MENSAGEM_TAMANHO) String busca,
            @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC) Pageable pageable) {
        return new PagedModel<>(disciplinaService.buscar(nome, ativa, busca, pageable));
    }

    @GetMapping("/{id}")
    public DisciplinaResponse buscarPorId(@PathVariable Long id) {
        return disciplinaService.buscarPorId(id);
    }

    @PutMapping("/{id}")
    public DisciplinaResponse atualizar(@PathVariable Long id, @Valid @RequestBody DisciplinaRequest request) {
        return disciplinaService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        disciplinaService.excluir(id);
    }
}
