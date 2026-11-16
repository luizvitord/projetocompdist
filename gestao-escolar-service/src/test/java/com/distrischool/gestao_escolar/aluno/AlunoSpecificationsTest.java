package com.distrischool.gestao_escolar.aluno;

import com.distrischool.gestao_escolar.EscolaFixture;
import com.distrischool.gestao_escolar.PostgresJpaTest;
import com.distrischool.gestao_escolar.matricula.MatriculaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlunoSpecificationsTest extends PostgresJpaTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private AlunoRepository alunoRepository;
    @Autowired
    private MatriculaRepository matriculaRepository;

    private AlunoService service;

    @BeforeEach
    void dados() {
        EscolaFixture.criar(em);
        service = new AlunoService(alunoRepository, matriculaRepository, evento -> { });
    }

    private List<String> buscar(String nome, Long turmaId, String busca) {
        return service.buscar(nome, null, turmaId, busca, PageRequest.of(0, 20, Sort.by("matricula")))
                .map(AlunoResponse::nome).getContent();
    }

    @Test
    void buscaPorCadaCampo() {
        assertThat(buscar(null, null, "conceição")).containsExactly("João Conceição");
        assertThat(buscar(null, null, "000002")).containsExactly("Ana Lúcia Prado");
        assertThat(buscar(null, null, "@exemplo.org")).containsExactly("Ana Lúcia Prado");
    }

    @Test
    void ignoraMaiusculasEAcentos() {
        assertThat(buscar(null, null, "JOAO CONCEICAO")).containsExactly("João Conceição");
        assertThat(buscar(null, null, "lucia")).containsExactly("Ana Lúcia Prado");
    }

    @Test
    void combinaComOsFiltrosExistentes() {
        assertThat(buscar(null, null, "2026")).containsExactly("João Conceição", "Ana Lúcia Prado");
        assertThat(buscar(null, 1L, "2026")).containsExactly("João Conceição");
        assertThat(buscar("ana", null, "2026")).containsExactly("Ana Lúcia Prado");
    }

    @Test
    void alunoSemEmailContinuaEncontradoPelosOutrosCampos() {
        assertThat(buscar(null, null, "alves")).containsExactly("Pedro Alves");
    }

    @Test
    void buscaVaziaOuSoComEspacosNaoFiltra() {
        assertThat(buscar(null, null, "")).hasSize(3);
        assertThat(buscar(null, null, "   ")).hasSize(3);
    }
}
