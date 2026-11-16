package com.distrischool.gestao_administrativa.tecnico;

import com.distrischool.gestao_administrativa.PostgresJpaTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TecnicoSpecificationsTest extends PostgresJpaTest {

    @Autowired
    private TecnicoRepository repository;

    private TecnicoService service;

    @BeforeEach
    void dados() {
        // buscar() não publica eventos
        service = new TecnicoService(repository, evento -> { });
        salvar("Ângela Souza", "52998224725", Cargo.SECRETARIO, "Secretaria Acadêmica", "angela@escola.com", true);
        salvar("Bruno Ávila", "11144477735", Cargo.TECNICO_TI, "Tecnologia", "bruno.avila@escola.com", true);
        salvar("Célia Nunes", "39053344705", Cargo.SECRETARIO, "Biblioteca", "celia@exemplo.org", false);
    }

    private void salvar(String nome, String cpf, Cargo cargo, String setor, String email, boolean ativo) {
        Tecnico tecnico = new Tecnico();
        tecnico.setNome(nome);
        tecnico.setCpf(cpf);
        tecnico.setCargo(cargo);
        tecnico.setSetor(setor);
        tecnico.setEmail(email);
        tecnico.setDataAdmissao(LocalDate.of(2024, 2, 1));
        tecnico.setAtivo(ativo);
        repository.save(tecnico);
    }

    private List<String> buscar(Cargo cargo, boolean ativo, String busca) {
        return service.buscar(null, cargo, null, ativo, busca, PageRequest.of(0, 20, Sort.by("nome")))
                .map(TecnicoResponse::nome).getContent();
    }

    @Test
    void buscaPorCadaCampo() {
        assertThat(buscar(null, true, "angela")).containsExactly("Ângela Souza");
        assertThat(buscar(null, true, "bruno.avila@")).containsExactly("Bruno Ávila");
        assertThat(buscar(null, true, "academica")).containsExactly("Ângela Souza");
    }

    @Test
    void nuncaPesquisaOCpf() {
        assertThat(buscar(null, true, "52998224725")).isEmpty();
        assertThat(buscar(null, true, "529982")).isEmpty();
    }

    @Test
    void ignoraMaiusculasEAcentos() {
        assertThat(buscar(null, true, "ÁVILA")).containsExactly("Bruno Ávila");
        assertThat(buscar(null, true, "SECRETARIA ACADEMICA")).containsExactly("Ângela Souza");
    }

    @Test
    void combinaComCargoEAtivo() {
        assertThat(buscar(null, true, "escola.com")).containsExactlyInAnyOrder("Ângela Souza", "Bruno Ávila");
        assertThat(buscar(Cargo.TECNICO_TI, true, "escola.com")).containsExactly("Bruno Ávila");
        // Inativos continuam fora, a não ser que o filtro ativo=false seja pedido
        assertThat(buscar(null, true, "celia")).isEmpty();
        assertThat(buscar(null, false, "celia")).containsExactly("Célia Nunes");
    }

    @Test
    void buscaVaziaOuSoComEspacosNaoFiltra() {
        assertThat(buscar(null, true, "")).hasSize(2);
        assertThat(buscar(null, true, "  ")).hasSize(2);
    }
}
