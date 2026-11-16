package com.distrischool.gestao_administrativa.disciplina;

import com.distrischool.gestao_administrativa.PostgresJpaTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DisciplinaSpecificationsTest extends PostgresJpaTest {

    @Autowired
    private DisciplinaRepository repository;

    private DisciplinaService service;

    @BeforeEach
    void dados() {
        service = new DisciplinaService(repository, evento -> { });
        salvar("MAT-9", "Matemática", true);
        salvar("CIE-9", "Ciências", true);
        salvar("EDF-9", "Educação Física", true);
        salvar("LAT-1", "Latim", false);
    }

    private void salvar(String codigo, String nome, boolean ativa) {
        Disciplina disciplina = new Disciplina();
        disciplina.setCodigo(codigo);
        disciplina.setNome(nome);
        disciplina.setAtiva(ativa);
        repository.save(disciplina);
    }

    private List<String> buscar(String nome, boolean ativa, String busca) {
        return service.buscar(nome, ativa, busca, PageRequest.of(0, 20, Sort.by("nome")))
                .map(DisciplinaResponse::nome).getContent();
    }

    @Test
    void buscaPorCodigoOuNome() {
        assertThat(buscar(null, true, "mat-")).containsExactly("Matemática");
        assertThat(buscar(null, true, "fisica")).containsExactly("Educação Física");
        assertThat(buscar(null, true, "-9")).containsExactly("Ciências", "Educação Física", "Matemática");
    }

    @Test
    void ignoraMaiusculasEAcentos() {
        assertThat(buscar(null, true, "EDUCACAO")).containsExactly("Educação Física");
        assertThat(buscar(null, true, "ciencias")).containsExactly("Ciências");
    }

    @Test
    void combinaComNomeEAtiva() {
        assertThat(buscar("ci", true, "-9")).containsExactly("Ciências");
        assertThat(buscar(null, true, "latim")).isEmpty();
        assertThat(buscar(null, false, "lat")).containsExactly("Latim");
    }

    @Test
    void buscaVaziaOuSoComEspacosNaoFiltra() {
        assertThat(buscar(null, true, "")).hasSize(3);
        assertThat(buscar(null, true, " \t ")).hasSize(3);
    }
}
