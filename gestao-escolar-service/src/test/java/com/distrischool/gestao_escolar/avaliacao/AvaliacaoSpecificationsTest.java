package com.distrischool.gestao_escolar.avaliacao;

import com.distrischool.gestao_escolar.EscolaFixture;
import com.distrischool.gestao_escolar.PostgresJpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AvaliacaoSpecificationsTest extends PostgresJpaTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private AvaliacaoRepository avaliacaoRepository;

    private AvaliacaoService service;

    @BeforeEach
    void dados() {
        EscolaFixture.criar(em);
        // buscar() usa só o repositório
        service = new AvaliacaoService(avaliacaoRepository, null, null, null);
    }

    private List<String> buscar(Long turmaId, Long disciplinaId, Integer bimestre, String busca) {
        return service.buscar(turmaId, disciplinaId, bimestre, busca, PageRequest.of(0, 20, Sort.by("descricao")))
                .map(AvaliacaoResponse::descricao).getContent();
    }

    @Test
    void buscaPelaDescricao() {
        assertThat(buscar(null, null, null, "avaliacao teste")).containsExactly("Avaliação Teste");
        assertThat(buscar(null, null, null, "PESQUISA")).containsExactly("Trabalho de pesquisa");
    }

    @Test
    void buscaPelaTurma() {
        assertThat(buscar(null, null, null, "1em-2026")).containsExactly("Trabalho de pesquisa");
        assertThat(buscar(null, null, null, "9º ano a")).containsExactly("Avaliação Teste", "Prova final");
    }

    @Test
    void buscaPeloNomeDaDisciplinaNaquelaTurma() {
        assertThat(buscar(null, null, null, "matematica")).containsExactly("Avaliação Teste", "Prova final");
        assertThat(buscar(null, null, null, "história")).containsExactly("Trabalho de pesquisa");
    }

    @Test
    void combinaComTurmaDisciplinaEBimestre() {
        assertThat(buscar(1L, null, null, "matematica")).containsExactly("Avaliação Teste");
        assertThat(buscar(null, 3L, 4, "matematica")).containsExactly("Prova final");
        assertThat(buscar(2L, null, null, "matematica")).isEmpty();
    }

    @Test
    void buscaVaziaOuSoComEspacosNaoFiltra() {
        assertThat(buscar(null, null, null, "")).hasSize(3);
        assertThat(buscar(null, null, null, "   ")).hasSize(3);
    }

    @Test
    void ordenacaoPedidaPeloClienteColocaAsMaisRecentesPrimeiro() {
        Page<AvaliacaoResponse> pagina = service.buscar(null, null, null, null,
                PageRequest.of(0, 2, Sort.by(Sort.Order.desc("data"), Sort.Order.desc("id"))));
        assertThat(pagina.map(AvaliacaoResponse::descricao).getContent())
                .containsExactly("Prova final", "Trabalho de pesquisa");
        assertThat(pagina.getTotalElements()).isEqualTo(3);
    }
}
