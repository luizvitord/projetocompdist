package com.distrischool.gestao_escolar.avaliacao;

import com.distrischool.gestao_escolar.aluno.AlunoService;
import com.distrischool.gestao_escolar.avaliacao.CalculoMedias.NotaAvaliacao;
import com.distrischool.gestao_escolar.avaliacao.CalculoMedias.ResultadoDisciplina;
import com.distrischool.gestao_escolar.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_escolar.frequencia.CalculoFrequencia;
import com.distrischool.gestao_escolar.frequencia.RegistroFrequenciaRepository;
import com.distrischool.gestao_escolar.frequencia.RegistroFrequenciaRepository.ContagemPorMatriculaEDisciplina;
import com.distrischool.gestao_escolar.frequencia.ResumoFrequencia;
import com.distrischool.gestao_escolar.matricula.Matricula;
import com.distrischool.gestao_escolar.matricula.MatriculaRepository;
import com.distrischool.gestao_escolar.turma.TurmaDisciplinaReplica;
import com.distrischool.gestao_escolar.turma.TurmaReplica;
import com.distrischool.gestao_escolar.turma.TurmaReplicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Monta o boletim: considera todas as matrículas do aluno no ano letivo (inclusive
 * {@code TRANSFERIDA}) e agrupa notas e frequência por disciplina. A situação vem do status da
 * matrícula mais recente.
 * <p>
 * Uma avaliação entra no boletim se o aluno tem nota nela ou se a data dela cai no período de
 * uma matrícula do aluno naquela turma: quem chegou por transferência não fica com pendências de
 * avaliações anteriores à chegada, nem com as da turma de origem posteriores à saída.
 */
@Service
@RequiredArgsConstructor
public class BoletimService {

    private final AlunoService alunoService;
    private final MatriculaRepository matriculaRepository;
    private final TurmaReplicaService turmaReplicaService;
    private final AvaliacaoRepository avaliacaoRepository;
    private final NotaRepository notaRepository;
    private final RegistroFrequenciaRepository registroFrequenciaRepository;
    private final CalculoMedias calculoMedias;
    private final CalculoFrequencia calculoFrequencia;

    @Transactional(readOnly = true)
    public BoletimResponse boletim(Long alunoId, Integer anoLetivo) {
        alunoService.obter(alunoId);
        List<Matricula> matriculas = matriculaRepository.findByAlunoIdAndAnoLetivoOrderByIdAsc(alunoId, anoLetivo);
        if (matriculas.isEmpty()) {
            throw new RecursoNaoEncontradoException("Matrícula do aluno " + alunoId + " no ano letivo", anoLetivo);
        }
        Matricula atual = matriculas.getLast();
        Map<Long, TurmaReplica> turmas = new LinkedHashMap<>();
        matriculas.forEach(m -> turmas.computeIfAbsent(m.getTurmaId(), turmaReplicaService::obter));
        List<Long> matriculaIds = matriculas.stream().map(Matricula::getId).toList();

        Map<Long, List<NotaAvaliacao>> notasPorDisciplina = notasPorDisciplina(matriculas, turmas.keySet(), matriculaIds);
        Map<Long, ResumoFrequencia> frequenciaPorDisciplina = frequenciaPorDisciplina(matriculaIds);
        ResumoFrequencia frequenciaGeral = calculoFrequencia.somar(frequenciaPorDisciplina.values());

        // Disciplinas com avaliações ou chamadas do aluno, mais as ofertadas hoje na turma atual
        Set<Long> disciplinaIds = new LinkedHashSet<>(notasPorDisciplina.keySet());
        disciplinaIds.addAll(frequenciaPorDisciplina.keySet());
        turmas.get(atual.getTurmaId()).getDisciplinas().stream()
                .filter(d -> !d.isRemovida())
                .forEach(d -> disciplinaIds.add(d.getId().getDisciplinaId()));

        List<BoletimResponse.Disciplina> disciplinas = new ArrayList<>();
        List<Boolean> atingiram = new ArrayList<>();
        for (Long disciplinaId : disciplinaIds) {
            ResultadoDisciplina resultado = calculoMedias.calcular(notasPorDisciplina.getOrDefault(disciplinaId, List.of()));
            atingiram.add(resultado.atingiuMedia());
            TurmaDisciplinaReplica oferta = oferta(disciplinaId, atual, turmas);
            disciplinas.add(new BoletimResponse.Disciplina(
                    disciplinaId,
                    oferta != null ? oferta.getCodigo() : null,
                    oferta != null ? oferta.getNome() : null,
                    resultado.bimestres().stream()
                            .map(b -> new BoletimResponse.Bimestre(b.bimestre(), b.media(), b.mediaSemRecuperacao(),
                                    b.recuperacao(), b.avaliacoesPendentes()))
                            .toList(),
                    resultado.mediaAnual(),
                    frequenciaPorDisciplina.getOrDefault(disciplinaId, calculoFrequencia.resumir(0, 0, 0)),
                    resultado.avaliacoesPendentes(),
                    RegrasAprovacao.situacaoDisciplina(atual.getStatus(), frequenciaGeral.abaixoDoMinimo(),
                            resultado.atingiuMedia())));
        }
        disciplinas.sort(Comparator.comparing(BoletimResponse.Disciplina::codigo, Comparator.nullsLast(Comparator.naturalOrder())));

        List<BoletimResponse.MatriculaResumo> resumoMatriculas = matriculas.stream()
                .map(m -> new BoletimResponse.MatriculaResumo(m.getId(), m.getTurmaId(), turmas.get(m.getTurmaId()).getCodigo(),
                        m.getStatus(), m.getDataMatricula(), m.getDataEncerramento()))
                .toList();
        return new BoletimResponse(alunoId, anoLetivo, calculoMedias.getMediaAprovacao(),
                calculoFrequencia.getMinimoPercentual(),
                RegrasAprovacao.situacaoGeral(atual.getStatus(), frequenciaGeral.abaixoDoMinimo(), atingiram),
                frequenciaGeral, resumoMatriculas, disciplinas);
    }

    private Map<Long, List<NotaAvaliacao>> notasPorDisciplina(List<Matricula> matriculas, Set<Long> turmaIds,
                                                              List<Long> matriculaIds) {
        // Se o aluno tiver duas matrículas na mesma turma (cancelou e voltou), vale a nota mais recente
        Map<Long, Nota> notaPorAvaliacao = new HashMap<>();
        notaRepository.findByMatriculaIdIn(matriculaIds).stream()
                .sorted(Comparator.comparing(Nota::getMatriculaId))
                .forEach(n -> notaPorAvaliacao.put(n.getAvaliacao().getId(), n));

        Map<Long, List<NotaAvaliacao>> porDisciplina = new LinkedHashMap<>();
        for (Avaliacao avaliacao : avaliacaoRepository.findByTurmaIdIn(turmaIds)) {
            Nota nota = notaPorAvaliacao.get(avaliacao.getId());
            boolean noPeriodo = matriculas.stream().anyMatch(m ->
                    m.getTurmaId().equals(avaliacao.getTurmaId()) && periodoInclui(m, avaliacao.getData()));
            if (nota == null && !noPeriodo) {
                continue;
            }
            porDisciplina.computeIfAbsent(avaliacao.getDisciplinaId(), id -> new ArrayList<>())
                    .add(new NotaAvaliacao(avaliacao.getBimestre(), avaliacao.getTipo(), avaliacao.getPeso(),
                            avaliacao.getNotaMaxima(), nota != null ? nota.getValor() : null));
        }
        return porDisciplina;
    }

    private Map<Long, ResumoFrequencia> frequenciaPorDisciplina(List<Long> matriculaIds) {
        Map<Long, long[]> somas = new LinkedHashMap<>();
        for (ContagemPorMatriculaEDisciplina c : registroFrequenciaRepository.contarPorMatriculaEDisciplina(matriculaIds)) {
            long[] soma = somas.computeIfAbsent(c.disciplinaId(), id -> new long[3]);
            soma[0] += c.presencas();
            soma[1] += c.faltas();
            soma[2] += c.atrasos();
        }
        Map<Long, ResumoFrequencia> resumos = new LinkedHashMap<>();
        somas.forEach((disciplinaId, s) -> resumos.put(disciplinaId, calculoFrequencia.resumir(s[0], s[1], s[2])));
        return resumos;
    }

    private static boolean periodoInclui(Matricula matricula, LocalDate data) {
        return !data.isBefore(matricula.getDataMatricula())
                && (matricula.getDataEncerramento() == null || !data.isAfter(matricula.getDataEncerramento()));
    }

    /** Código e nome da disciplina, preferindo a turma atual (a cópia mais recente). */
    private static TurmaDisciplinaReplica oferta(Long disciplinaId, Matricula atual, Map<Long, TurmaReplica> turmas) {
        return turmas.get(atual.getTurmaId()).disciplina(disciplinaId)
                .or(() -> turmas.values().stream()
                        .flatMap(t -> t.disciplina(disciplinaId).stream())
                        .findFirst())
                .orElse(null);
    }
}
