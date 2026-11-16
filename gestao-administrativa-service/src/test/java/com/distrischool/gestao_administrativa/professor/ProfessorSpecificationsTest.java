package com.distrischool.gestao_administrativa.professor;

import com.distrischool.gestao_administrativa.PostgresJpaTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessorSpecificationsTest extends PostgresJpaTest {

    @Autowired
    private ProfessorRepository repository;

    private ProfessorService service;

    @BeforeEach
    void dados() {
        service = new ProfessorService(repository);
        salvar("José Antônio Câmara", "jose.camara@escola.com", "Licenciatura em Matemática");
        salvar("Maria Lúcia Prado", "maria.prado@escola.com", "Mestrado em História");
        salvar("Carlos Lima", "carlos@exemplo.org", "Doutorado em Física");
    }

    private void salvar(String nome, String email, String qualificacao) {
        Professor professor = new Professor();
        professor.setNome(nome);
        professor.setEmail(email);
        professor.setQualificacao(qualificacao);
        repository.save(professor);
    }

    private List<String> buscar(String nome, String busca) {
        return service.buscar(nome, busca, PageRequest.of(0, 20, Sort.by("nome")))
                .map(ProfessorResponse::nome).getContent();
    }

    @Test
    void buscaPorCadaCampo() {
        assertThat(buscar(null, "câmara")).containsExactly("José Antônio Câmara");
        assertThat(buscar(null, "maria.prado@")).containsExactly("Maria Lúcia Prado");
        assertThat(buscar(null, "física")).containsExactly("Carlos Lima");
    }

    @Test
    void ignoraMaiusculasEAcentos() {
        assertThat(buscar(null, "JOSE ANTONIO")).containsExactly("José Antônio Câmara");
        assertThat(buscar(null, "matematica")).containsExactly("José Antônio Câmara");
        assertThat(buscar(null, "HISTÓRIA")).containsExactly("Maria Lúcia Prado");
    }

    @Test
    void combinaComOFiltroNome() {
        assertThat(buscar(null, "escola.com")).containsExactly("José Antônio Câmara", "Maria Lúcia Prado");
        assertThat(buscar("maria", "escola.com")).containsExactly("Maria Lúcia Prado");
        assertThat(buscar("carlos", "escola.com")).isEmpty();
    }

    @Test
    void buscaVaziaOuSoComEspacosNaoFiltra() {
        assertThat(buscar(null, "")).hasSize(3);
        assertThat(buscar(null, "   ")).hasSize(3);
        assertThat(buscar(null, null)).hasSize(3);
    }

    @Test
    void curingasEAspasDigitadosValemComoTexto() {
        salvar("Ana D'Ávila", "ana_davila@escola.com", "Pedagogia");
        assertThat(buscar(null, "%")).isEmpty();
        assertThat(buscar(null, "_davila")).containsExactly("Ana D'Ávila");
        assertThat(buscar(null, "d'avila")).containsExactly("Ana D'Ávila");
    }
}
