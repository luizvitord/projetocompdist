package com.distrischool.gestao_escolar.avaliacao;

import com.distrischool.gestao_escolar.aluno.AlunoService;
import com.distrischool.gestao_escolar.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_escolar.frequencia.CalculoFrequencia;
import com.distrischool.gestao_escolar.frequencia.RegistroFrequenciaRepository;
import com.distrischool.gestao_escolar.frequencia.RegistroFrequenciaRepository.ContagemPorMatriculaEDisciplina;
import com.distrischool.gestao_escolar.matricula.Matricula;
import com.distrischool.gestao_escolar.matricula.MatriculaRepository;
import com.distrischool.gestao_escolar.matricula.StatusMatricula;
import com.distrischool.gestao_escolar.turma.TurmaDisciplinaReplica;
import com.distrischool.gestao_escolar.turma.TurmaReplica;
import com.distrischool.gestao_escolar.turma.TurmaReplicaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Boletim de um aluno transferido no meio do ano: 9A até 30/04, 9B a partir de 01/05. Sem Spring,
 * com repositórios simulados.
 */
class BoletimServiceTest {

    private static final long MAT = 3L;
    private static final long POR = 4L;

    private MatriculaRepository matriculaRepository;
    private AvaliacaoRepository avaliacaoRepository;
    private NotaRepository notaRepository;
    private RegistroFrequenciaRepository registroRepository;
    private BoletimService service;

    private final List<Matricula> matriculas = new ArrayList<>();
    private final List<Avaliacao> avaliacoes = new ArrayList<>();
    private final List<Nota> notas = new ArrayList<>();
    private final List<ContagemPorMatriculaEDisciplina> frequencias = new ArrayList<>();

    private Matricula origem;
    private Matricula atual;

    @BeforeEach
    void preparar() {
        matriculaRepository = mock(MatriculaRepository.class);
        avaliacaoRepository = mock(AvaliacaoRepository.class);
        notaRepository = mock(NotaRepository.class);
        registroRepository = mock(RegistroFrequenciaRepository.class);
        TurmaReplicaService turmaReplicaService = mock(TurmaReplicaService.class);
        service = new BoletimService(mock(AlunoService.class), matriculaRepository, turmaReplicaService,
                avaliacaoRepository, notaRepository, registroRepository,
                new CalculoMedias(new BigDecimal("6.0")), new CalculoFrequencia(new BigDecimal("75")));

        when(turmaReplicaService.obter(12L)).thenReturn(turma(12L, "9A-2026"));
        when(turmaReplicaService.obter(15L)).thenReturn(turma(15L, "9B-2026"));
        when(matriculaRepository.findByAlunoIdAndAnoLetivoOrderByIdAsc(1L, 2026)).thenReturn(matriculas);
        when(avaliacaoRepository.findByTurmaIdIn(any())).thenReturn(avaliacoes);
        when(notaRepository.findByMatriculaIdIn(any())).thenReturn(notas);
        when(registroRepository.contarPorMatriculaEDisciplina(any())).thenReturn(frequencias);

        origem = matricula(40L, 12L, StatusMatricula.TRANSFERIDA, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30));
        atual = matricula(41L, 15L, StatusMatricula.ATIVA, LocalDate.of(2026, 5, 1), null);
    }

    private static TurmaReplica turma(Long id, String codigo) {
        TurmaReplica t = new TurmaReplica(id);
        t.setCodigo(codigo);
        t.setAnoLetivo(2026);
        for (long disciplina : new long[]{MAT, POR}) {
            TurmaDisciplinaReplica oferta = new TurmaDisciplinaReplica(t, disciplina);
            oferta.setCodigo(disciplina == MAT ? "MAT" : "POR");
            oferta.setNome(disciplina == MAT ? "Matemática" : "Português");
            t.getDisciplinas().add(oferta);
        }
        return t;
    }

    private Matricula matricula(Long id, Long turmaId, StatusMatricula status, LocalDate inicio, LocalDate fim) {
        Matricula m = new Matricula();
        m.setId(id);
        m.setAlunoId(1L);
        m.setTurmaId(turmaId);
        m.setAnoLetivo(2026);
        m.setStatus(status);
        m.setDataMatricula(inicio);
        m.setDataEncerramento(fim);
        matriculas.add(m);
        return m;
    }

    private Avaliacao avaliacao(Long id, Long turmaId, long disciplinaId, int bimestre, TipoAvaliacao tipo, LocalDate data) {
        Avaliacao a = new Avaliacao();
        a.setId(id);
        a.setTurmaId(turmaId);
        a.setDisciplinaId(disciplinaId);
        a.setBimestre(bimestre);
        a.setTipo(tipo);
        a.setPeso(BigDecimal.ONE);
        a.setNotaMaxima(BigDecimal.TEN);
        a.setData(data);
        avaliacoes.add(a);
        return a;
    }

    private void nota(Avaliacao avaliacao, Matricula matricula, String valor) {
        Nota n = new Nota();
        n.setAvaliacao(avaliacao);
        n.setMatriculaId(matricula.getId());
        n.setValor(new BigDecimal(valor));
        notas.add(n);
    }

    private BoletimResponse.Disciplina disciplina(BoletimResponse boletim, long disciplinaId) {
        return boletim.disciplinas().stream().filter(d -> d.disciplinaId() == disciplinaId).findFirst().orElseThrow();
    }

    @Test
    void juntaAsNotasDasDuasTurmasNaMesmaDisciplina() {
        nota(avaliacao(1L, 12L, MAT, 1, TipoAvaliacao.PROVA, LocalDate.of(2026, 3, 20)), origem, "5");
        nota(avaliacao(2L, 15L, MAT, 2, TipoAvaliacao.PROVA, LocalDate.of(2026, 6, 10)), atual, "9");

        BoletimResponse boletim = service.boletim(1L, 2026);

        BoletimResponse.Disciplina mat = disciplina(boletim, MAT);
        assertThat(mat.bimestres().get(0).media()).isEqualByComparingTo("5.00");
        assertThat(mat.bimestres().get(1).media()).isEqualByComparingTo("9.00");
        assertThat(mat.mediaAnual()).isEqualByComparingTo("7.00");
        assertThat(boletim.matriculas()).extracting(BoletimResponse.MatriculaResumo::status)
                .containsExactly(StatusMatricula.TRANSFERIDA, StatusMatricula.ATIVA);
        assertThat(boletim.matriculas()).extracting(BoletimResponse.MatriculaResumo::turmaCodigo)
                .containsExactly("9A-2026", "9B-2026");
    }

    @Test
    void pendenciasSoContamNoPeriodoDeCadaMatricula() {
        // 9A depois da saída e 9B antes da chegada não são pendências do aluno
        avaliacao(1L, 12L, MAT, 2, TipoAvaliacao.PROVA, LocalDate.of(2026, 6, 10));
        avaliacao(2L, 15L, MAT, 1, TipoAvaliacao.PROVA, LocalDate.of(2026, 3, 20));
        // Estas estão no período e sem nota: pendentes
        avaliacao(3L, 12L, MAT, 1, TipoAvaliacao.TRABALHO, LocalDate.of(2026, 4, 30));
        avaliacao(4L, 15L, MAT, 2, TipoAvaliacao.TRABALHO, LocalDate.of(2026, 5, 1));

        BoletimResponse.Disciplina mat = disciplina(service.boletim(1L, 2026), MAT);

        assertThat(mat.avaliacoesPendentes()).isEqualTo(2);
        assertThat(mat.bimestres().get(0).avaliacoesPendentes()).isEqualTo(1);
        assertThat(mat.bimestres().get(1).avaliacoesPendentes()).isEqualTo(1);
    }

    @Test
    void avaliacaoComNotaSempreEntraMesmoForaDoPeriodo() {
        nota(avaliacao(1L, 15L, MAT, 1, TipoAvaliacao.PROVA, LocalDate.of(2026, 3, 20)), atual, "8");

        assertThat(disciplina(service.boletim(1L, 2026), MAT).mediaAnual()).isEqualByComparingTo("8.00");
    }

    @Test
    void frequenciaSomaAsDuasMatriculas() {
        frequencias.add(new ContagemPorMatriculaEDisciplina(40L, MAT, 8, 2, 0));
        frequencias.add(new ContagemPorMatriculaEDisciplina(41L, MAT, 9, 0, 1));
        frequencias.add(new ContagemPorMatriculaEDisciplina(41L, POR, 5, 5, 0));

        BoletimResponse boletim = service.boletim(1L, 2026);

        assertThat(disciplina(boletim, MAT).frequencia().totalAulas()).isEqualTo(20);
        assertThat(disciplina(boletim, MAT).frequencia().percentual()).isEqualByComparingTo("90.00");
        assertThat(disciplina(boletim, POR).frequencia().percentual()).isEqualByComparingTo("50.00");
        // Geral: (8 + 9 + 1 + 5) / 30
        assertThat(boletim.frequenciaGeral().percentual()).isEqualByComparingTo("76.67");
        assertThat(boletim.frequenciaGeral().abaixoDoMinimo()).isFalse();
    }

    @Test
    void matriculaAtivaFicaCursando() {
        nota(avaliacao(1L, 15L, MAT, 2, TipoAvaliacao.PROVA, LocalDate.of(2026, 6, 10)), atual, "2");

        BoletimResponse boletim = service.boletim(1L, 2026);

        assertThat(boletim.situacao()).isEqualTo(SituacaoBoletim.CURSANDO);
        assertThat(boletim.disciplinas()).allSatisfy(d -> assertThat(d.situacao()).isEqualTo(SituacaoBoletim.CURSANDO));
    }

    @Test
    void concluidaComTodasNaMediaEFrequenciaOkEAprovada() {
        atual.setStatus(StatusMatricula.CONCLUIDA);
        nota(avaliacao(1L, 15L, MAT, 2, TipoAvaliacao.PROVA, LocalDate.of(2026, 6, 10)), atual, "7");
        nota(avaliacao(2L, 15L, POR, 2, TipoAvaliacao.PROVA, LocalDate.of(2026, 6, 10)), atual, "6");
        frequencias.add(new ContagemPorMatriculaEDisciplina(41L, MAT, 10, 0, 0));

        BoletimResponse boletim = service.boletim(1L, 2026);

        assertThat(boletim.situacao()).isEqualTo(SituacaoBoletim.APROVADO);
    }

    @Test
    void concluidaComUmaDisciplinaAbaixoReprovaPorNota() {
        atual.setStatus(StatusMatricula.CONCLUIDA);
        nota(avaliacao(1L, 15L, MAT, 2, TipoAvaliacao.PROVA, LocalDate.of(2026, 6, 10)), atual, "7");
        nota(avaliacao(2L, 15L, POR, 2, TipoAvaliacao.PROVA, LocalDate.of(2026, 6, 10)), atual, "5.99");

        BoletimResponse boletim = service.boletim(1L, 2026);

        assertThat(boletim.situacao()).isEqualTo(SituacaoBoletim.REPROVADO_POR_NOTA);
        assertThat(disciplina(boletim, MAT).situacao()).isEqualTo(SituacaoBoletim.APROVADO);
        assertThat(disciplina(boletim, POR).situacao()).isEqualTo(SituacaoBoletim.REPROVADO_POR_NOTA);
    }

    @Test
    void concluidaComFrequenciaGeralAbaixoReprovaPorFalta() {
        atual.setStatus(StatusMatricula.CONCLUIDA);
        nota(avaliacao(1L, 15L, MAT, 2, TipoAvaliacao.PROVA, LocalDate.of(2026, 6, 10)), atual, "10");
        nota(avaliacao(2L, 15L, POR, 2, TipoAvaliacao.PROVA, LocalDate.of(2026, 6, 10)), atual, "10");
        frequencias.add(new ContagemPorMatriculaEDisciplina(41L, MAT, 7, 3, 0));

        assertThat(service.boletim(1L, 2026).situacao()).isEqualTo(SituacaoBoletim.REPROVADO_POR_FALTA);
    }

    @Test
    void disciplinasOfertadasNaTurmaAtualAparecemMesmoSemNotas() {
        BoletimResponse boletim = service.boletim(1L, 2026);

        assertThat(boletim.disciplinas()).extracting(BoletimResponse.Disciplina::codigo).containsExactly("MAT", "POR");
        assertThat(boletim.disciplinas()).allSatisfy(d -> {
            assertThat(d.mediaAnual()).isNull();
            assertThat(d.bimestres()).hasSize(4);
            assertThat(d.frequencia().percentual()).isNull();
        });
    }

    @Test
    void semMatriculaNoAnoRetorna404() {
        matriculas.clear();

        assertThatThrownBy(() -> service.boletim(1L, 2026)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
