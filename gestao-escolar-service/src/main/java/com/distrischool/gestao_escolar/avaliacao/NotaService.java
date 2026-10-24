package com.distrischool.gestao_escolar.avaliacao;

import com.distrischool.gestao_escolar.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_escolar.common.RegraNegocioException;
import com.distrischool.gestao_escolar.common.ValidacaoException;
import com.distrischool.gestao_escolar.event.GradePostedEvent;
import com.distrischool.gestao_escolar.matricula.Matricula;
import com.distrischool.gestao_escolar.matricula.MatriculaRepository;
import com.distrischool.gestao_escolar.matricula.StatusMatricula;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Lançamento em lote, correção (com histórico) e consulta de notas. */
@Service
@RequiredArgsConstructor
public class NotaService {

    private final NotaRepository notaRepository;
    private final NotaHistoricoRepository historicoRepository;
    private final AvaliacaoService avaliacaoService;
    private final MatriculaRepository matriculaRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Lança as notas de uma avaliação, só para matrículas ATIVA da turma dela. Matrícula que já
     * tem nota → 409 (a correção é feita em {@link #corrigir}).
     */
    @Transactional
    public List<NotaResponse> lancar(Long avaliacaoId, List<LancamentoNotaRequest> lancamentos) {
        Avaliacao avaliacao = avaliacaoService.obter(avaliacaoId);
        Map<Long, Matricula> ativas = matriculaRepository.findByTurmaIdAndStatus(avaliacao.getTurmaId(), StatusMatricula.ATIVA)
                .stream().collect(Collectors.toMap(Matricula::getId, Function.identity()));
        validar(avaliacao, lancamentos, ativas);

        Set<Long> matriculaIds = lancamentos.stream().map(LancamentoNotaRequest::matriculaId).collect(Collectors.toSet());
        List<Long> jaLancadas = notaRepository.findByAvaliacaoIdAndMatriculaIdIn(avaliacaoId, matriculaIds).stream()
                .map(Nota::getMatriculaId).sorted().toList();
        if (!jaLancadas.isEmpty()) {
            throw jaLancadas(jaLancadas);
        }

        List<Nota> notas = new ArrayList<>();
        for (LancamentoNotaRequest lancamento : lancamentos) {
            Nota nota = new Nota();
            nota.setAvaliacao(avaliacao);
            nota.setMatriculaId(lancamento.matriculaId());
            nota.setValor(escala(lancamento.valor()));
            notas.add(nota);
        }
        try {
            notas = notaRepository.saveAllAndFlush(notas);
        } catch (DataIntegrityViolationException ex) {
            // Lançamento da mesma avaliação para a mesma matrícula em paralelo
            throw jaLancadas(List.copyOf(new TreeSet<>(matriculaIds)));
        }

        publicar(avaliacao, notas.stream()
                .map(n -> new GradePostedEvent.NotaAluno(ativas.get(n.getMatriculaId()).getAlunoId(), n.getValor()))
                .toList());
        return notas.stream().sorted(Comparator.comparing(Nota::getMatriculaId)).map(NotaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<NotaResponse> listar(Long avaliacaoId) {
        avaliacaoService.obter(avaliacaoId);
        return notaRepository.findByAvaliacaoIdOrderByMatriculaId(avaliacaoId).stream().map(NotaResponse::from).toList();
    }

    /** Corrige a nota e grava o valor anterior, o novo e o motivo em {@code nota_historico}. */
    @Transactional
    public NotaResponse corrigir(Long notaId, CorrecaoNotaRequest request) {
        Nota nota = notaRepository.findByIdParaAtualizacao(notaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nota", notaId));
        Avaliacao avaliacao = nota.getAvaliacao();
        BigDecimal novo = escala(request.valor());
        if (novo.compareTo(avaliacao.getNotaMaxima()) > 0) {
            throw ValidacaoException.campo("valor", "deve ser no máximo " + avaliacao.getNotaMaxima() + " (nota máxima da avaliação)");
        }
        if (novo.compareTo(nota.getValor()) == 0) {
            throw new RegraNegocioException("A nota já tem o valor " + nota.getValor());
        }

        NotaHistorico historico = new NotaHistorico();
        historico.setNotaId(nota.getId());
        historico.setValorAnterior(nota.getValor());
        historico.setValorNovo(novo);
        historico.setMotivo(request.motivo().trim());
        historicoRepository.save(historico);

        nota.setValor(novo);
        // Flush para a resposta já trazer o updatedAt da correção
        notaRepository.saveAndFlush(nota);

        Long alunoId = matriculaRepository.findById(nota.getMatriculaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Matrícula", nota.getMatriculaId()))
                .getAlunoId();
        publicar(avaliacao, List.of(new GradePostedEvent.NotaAluno(alunoId, novo)));
        return NotaResponse.from(nota);
    }

    @Transactional(readOnly = true)
    public List<NotaHistoricoResponse> historico(Long notaId) {
        if (!notaRepository.existsById(notaId)) {
            throw new RecursoNaoEncontradoException("Nota", notaId);
        }
        return historicoRepository.findByNotaIdOrderByAlteradoEmAscIdAsc(notaId).stream()
                .map(NotaHistoricoResponse::from).toList();
    }

    private static void validar(Avaliacao avaliacao, List<LancamentoNotaRequest> lancamentos, Map<Long, Matricula> ativas) {
        Set<Long> vistas = new HashSet<>();
        Set<Long> duplicadas = new TreeSet<>();
        Set<Long> naoAtivas = new TreeSet<>();
        Set<Long> acimaDoMaximo = new TreeSet<>();
        for (LancamentoNotaRequest lancamento : lancamentos) {
            Long matriculaId = lancamento.matriculaId();
            if (!vistas.add(matriculaId)) {
                duplicadas.add(matriculaId);
            }
            if (!ativas.containsKey(matriculaId)) {
                naoAtivas.add(matriculaId);
            }
            if (lancamento.valor().compareTo(avaliacao.getNotaMaxima()) > 0) {
                acimaDoMaximo.add(matriculaId);
            }
        }
        if (!duplicadas.isEmpty() || !naoAtivas.isEmpty() || !acimaDoMaximo.isEmpty()) {
            Map<String, Object> divergencias = new LinkedHashMap<>();
            divergencias.put("matriculasDuplicadas", List.copyOf(duplicadas));
            divergencias.put("matriculasNaoAtivasNaTurma", List.copyOf(naoAtivas));
            divergencias.put("matriculasComValorAcimaDoMaximo", List.copyOf(acimaDoMaximo));
            divergencias.put("notaMaxima", avaliacao.getNotaMaxima());
            throw new ValidacaoException("Lançamento inválido", divergencias);
        }
    }

    private void publicar(Avaliacao avaliacao, List<GradePostedEvent.NotaAluno> notas) {
        List<GradePostedEvent.NotaAluno> ordenadas = notas.stream()
                .sorted(Comparator.comparing(GradePostedEvent.NotaAluno::alunoId)).toList();
        eventPublisher.publishEvent(new GradePostedEvent(avaliacao.getId(), avaliacao.getTurmaId(),
                avaliacao.getDisciplinaId(), avaliacao.getBimestre(), ordenadas, Instant.now()));
    }

    private static RegraNegocioException jaLancadas(List<Long> matriculaIds) {
        return new RegraNegocioException("As matrículas " + matriculaIds
                + " já têm nota nesta avaliação; para corrigir, use PUT /api/notas/{id}");
    }

    private static BigDecimal escala(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
