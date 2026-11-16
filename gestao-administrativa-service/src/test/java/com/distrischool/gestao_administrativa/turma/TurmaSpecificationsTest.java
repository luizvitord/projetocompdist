package com.distrischool.gestao_administrativa.turma;

import com.distrischool.gestao_administrativa.PostgresJpaTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TurmaSpecificationsTest extends PostgresJpaTest {

    @Autowired
    private TurmaRepository repository;

    private TurmaService service;

    @BeforeEach
    void dados() {
        // buscar() usa só o repositório
        service = new TurmaService(repository, null, null, evento -> { });
        salvar("9A-2026", "9º Ano A", 2026, Turno.MANHA);
        salvar("9B-2026", "9º Ano B", 2026, Turno.TARDE);
        salvar("1EM-2026", "1ª Série Médio", 2026, Turno.MANHA);
        salvar("9A-2025", "9º Ano A", 2025, Turno.MANHA);
    }

    private void salvar(String codigo, String nome, int ano, Turno turno) {
        Turma turma = new Turma();
        turma.setCodigo(codigo);
        turma.setNome(nome);
        turma.setAnoLetivo(ano);
        turma.setTurno(turno);
        turma.setCapacidade(30);
        repository.save(turma);
    }

    private List<String> buscar(Integer ano, Turno turno, String busca) {
        return service.buscar(ano, turno, null, busca, PageRequest.of(0, 20, Sort.by("codigo")))
                .map(TurmaResponse::codigo).getContent();
    }

    @Test
    void buscaPorCodigoOuNome() {
        assertThat(buscar(null, null, "9b-")).containsExactly("9B-2026");
        assertThat(buscar(null, null, "ano a")).containsExactly("9A-2025", "9A-2026");
    }

    @Test
    void ignoraMaiusculasEAcentos() {
        assertThat(buscar(null, null, "SERIE MEDIO")).containsExactly("1EM-2026");
        // A mesma função unaccent é aplicada no campo e no texto digitado, então "º" casa com "º"
        assertThat(buscar(null, null, "9º ano")).containsExactly("9A-2025", "9A-2026", "9B-2026");
    }

    @Test
    void combinaComAnoLetivoETurno() {
        assertThat(buscar(2026, null, "ano a")).containsExactly("9A-2026");
        assertThat(buscar(2026, Turno.MANHA, "2026")).containsExactly("1EM-2026", "9A-2026");
    }

    @Test
    void buscaVaziaOuSoComEspacosNaoFiltra() {
        assertThat(buscar(2026, null, "")).hasSize(3);
        assertThat(buscar(2026, null, "   ")).hasSize(3);
    }
}
