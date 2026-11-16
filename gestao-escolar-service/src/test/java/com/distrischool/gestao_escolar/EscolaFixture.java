package com.distrischool.gestao_escolar;

import com.distrischool.gestao_escolar.aluno.Aluno;
import com.distrischool.gestao_escolar.avaliacao.Avaliacao;
import com.distrischool.gestao_escolar.avaliacao.TipoAvaliacao;
import com.distrischool.gestao_escolar.matricula.Matricula;
import com.distrischool.gestao_escolar.matricula.StatusMatricula;
import com.distrischool.gestao_escolar.turma.TurmaDisciplinaReplica;
import com.distrischool.gestao_escolar.turma.TurmaReplica;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados dos testes de busca, gravados direto pelo {@link EntityManager}:
 * <pre>
 * turma 1  9A-2026   "9º Ano A"         2026  oferta 3 Matemática
 * turma 2  1EM-2026  "1ª Série Médio"   2026  oferta 4 História
 * turma 3  9A-2025   "9º Ano A"         2025  oferta 3 Matemática
 *
 * João Conceição  2026000001  joao@escola.com        matrícula ATIVA na turma 1
 * Ana Lúcia Prado 2026000002  ana.lucia@exemplo.org  matrícula ATIVA na turma 2
 * Pedro Alves     2025000003  (sem email)            matrícula CONCLUIDA na turma 3
 *
 * "Avaliação Teste"       turma 1, Matemática, 1º bimestre
 * "Trabalho de pesquisa"  turma 2, História,   2º bimestre
 * "Prova final"           turma 3, Matemática, 4º bimestre
 * </pre>
 */
public final class EscolaFixture {

    public Aluno joao;
    public Aluno ana;
    public Aluno pedro;

    private final EntityManager em;

    private EscolaFixture(EntityManager em) {
        this.em = em;
    }

    public static EscolaFixture criar(EntityManager em) {
        EscolaFixture f = new EscolaFixture(em);
        f.turma(1L, "9A-2026", "9º Ano A", 2026, 3L, "MAT", "Matemática");
        f.turma(2L, "1EM-2026", "1ª Série Médio", 2026, 4L, "HIS", "História");
        f.turma(3L, "9A-2025", "9º Ano A", 2025, 3L, "MAT", "Matemática");

        f.joao = f.aluno("João Conceição", "2026000001", "joao@escola.com");
        f.ana = f.aluno("Ana Lúcia Prado", "2026000002", "ana.lucia@exemplo.org");
        f.pedro = f.aluno("Pedro Alves", "2025000003", null);

        f.matricula(f.joao, 1L, 2026, StatusMatricula.ATIVA);
        f.matricula(f.ana, 2L, 2026, StatusMatricula.ATIVA);
        f.matricula(f.pedro, 3L, 2025, StatusMatricula.CONCLUIDA);

        f.avaliacao("Avaliação Teste", 1L, 3L, 1);
        f.avaliacao("Trabalho de pesquisa", 2L, 4L, 2);
        f.avaliacao("Prova final", 3L, 3L, 4);
        em.flush();
        em.clear();
        return f;
    }

    private void turma(Long id, String codigo, String nome, int ano, Long disciplinaId, String codDisciplina,
                       String nomeDisciplina) {
        TurmaReplica turma = new TurmaReplica(id);
        turma.setCodigo(codigo);
        turma.setNome(nome);
        turma.setAnoLetivo(ano);
        turma.setTurno("MANHA");
        turma.setCapacidade(30);
        TurmaDisciplinaReplica oferta = new TurmaDisciplinaReplica(turma, disciplinaId);
        oferta.setCodigo(codDisciplina);
        oferta.setNome(nomeDisciplina);
        turma.getDisciplinas().add(oferta);
        em.persist(turma);
    }

    private Aluno aluno(String nome, String matricula, String email) {
        Aluno aluno = new Aluno();
        aluno.setNome(nome);
        aluno.setMatricula(matricula);
        aluno.setEmail(email);
        aluno.setDataNascimento(LocalDate.of(2012, 3, 10));
        em.persist(aluno);
        return aluno;
    }

    private void matricula(Aluno aluno, Long turmaId, int ano, StatusMatricula status) {
        Matricula matricula = new Matricula();
        matricula.setAlunoId(aluno.getId());
        matricula.setTurmaId(turmaId);
        matricula.setAnoLetivo(ano);
        matricula.setStatus(status);
        matricula.setDataMatricula(LocalDate.of(ano, 2, 1));
        if (status == StatusMatricula.CONCLUIDA) {
            matricula.setDataEncerramento(LocalDate.of(ano, 12, 15));
        }
        em.persist(matricula);
    }

    private void avaliacao(String descricao, Long turmaId, Long disciplinaId, int bimestre) {
        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setDescricao(descricao);
        avaliacao.setTurmaId(turmaId);
        avaliacao.setDisciplinaId(disciplinaId);
        avaliacao.setBimestre(bimestre);
        avaliacao.setTipo(TipoAvaliacao.PROVA);
        avaliacao.setPeso(BigDecimal.ONE);
        avaliacao.setNotaMaxima(BigDecimal.TEN);
        avaliacao.setData(LocalDate.of(2026, 3, bimestre));
        em.persist(avaliacao);
    }
}
