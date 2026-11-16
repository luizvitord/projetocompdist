package com.distrischool.gestao_escolar.matricula;

import com.distrischool.gestao_escolar.EscolaFixture;
import com.distrischool.gestao_escolar.PostgresJpaTest;
import com.distrischool.gestao_escolar.aluno.AlunoRepository;
import com.distrischool.gestao_escolar.aluno.AlunoService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatriculaSpecificationsTest extends PostgresJpaTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private AlunoRepository alunoRepository;
    @Autowired
    private MatriculaRepository matriculaRepository;

    private MatriculaService service;

    @BeforeEach
    void dados() {
        EscolaFixture.criar(em);
        AlunoService alunoService = new AlunoService(alunoRepository, matriculaRepository, evento -> { });
        // buscar() usa só o repositório e o AlunoService (nome e número de matrícula da resposta)
        service = new MatriculaService(matriculaRepository, alunoService, null, evento -> { }, null);
    }

    private List<String> buscar(Integer ano, StatusMatricula status, String busca) {
        return service.buscar(null, null, ano, status, busca, PageRequest.of(0, 20, Sort.by(Sort.Order.desc("id"))))
                .map(MatriculaResponse::alunoNome).getContent();
    }

    @Test
    void buscaPeloAluno() {
        assertThat(buscar(null, null, "conceicao")).containsExactly("João Conceição");
        assertThat(buscar(null, null, "2026000002")).containsExactly("Ana Lúcia Prado");
    }

    @Test
    void buscaPelaTurma() {
        assertThat(buscar(null, null, "1em-")).containsExactly("Ana Lúcia Prado");
        assertThat(buscar(null, null, "SERIE MEDIO")).containsExactly("Ana Lúcia Prado");
        // "9º Ano A" existe em 2025 e 2026; mais recentes primeiro (id desc)
        assertThat(buscar(null, null, "9º ano")).containsExactly("Pedro Alves", "João Conceição");
    }

    @Test
    void combinaComAnoLetivoEStatus() {
        assertThat(buscar(2026, null, "9º ano")).containsExactly("João Conceição");
        assertThat(buscar(null, StatusMatricula.CONCLUIDA, "9a-")).containsExactly("Pedro Alves");
        assertThat(buscar(2025, null, "ana")).isEmpty();
    }

    @Test
    void buscaVaziaOuSoComEspacosNaoFiltra() {
        assertThat(buscar(null, null, "")).hasSize(3);
        assertThat(buscar(null, null, "  ")).hasSize(3);
    }
}
