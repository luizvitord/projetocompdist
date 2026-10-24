package com.distrischool.gestao_escolar.frequencia;

import com.distrischool.gestao_escolar.aluno.AlunoService;
import com.distrischool.gestao_escolar.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_escolar.common.ValidacaoException;
import com.distrischool.gestao_escolar.turma.TurmaReplica;
import com.distrischool.gestao_escolar.turma.TurmaReplicaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

/** Consultas de frequência consolidada; as contagens são agregadas no banco. */
@Service
public class RelatorioFrequenciaService {

    private final RegistroFrequenciaRepository registroRepository;
    private final TurmaReplicaService turmaReplicaService;
    private final AlunoService alunoService;
    private final CalculoFrequencia calculo;
    private final TransactionTemplate leitura;

    public RelatorioFrequenciaService(RegistroFrequenciaRepository registroRepository,
                                      TurmaReplicaService turmaReplicaService,
                                      AlunoService alunoService,
                                      CalculoFrequencia calculo,
                                      PlatformTransactionManager transactionManager) {
        this.registroRepository = registroRepository;
        this.turmaReplicaService = turmaReplicaService;
        this.alunoService = alunoService;
        this.calculo = calculo;
        this.leitura = new TransactionTemplate(transactionManager);
        this.leitura.setReadOnly(true);
    }

    /**
     * Frequência por aluno da turma (em uma disciplina ou em todas). Sem datas, considera o ano
     * letivo inteiro da turma.
     */
    public RelatorioFrequenciaResponse relatorio(Long turmaId, Long disciplinaId, LocalDate dataInicio, LocalDate dataFim) {
        // O fallback (chamada HTTP) fica fora da transação de leitura
        turmaReplicaService.garantirNaReplica(turmaId);
        return leitura.execute(status -> montarRelatorio(turmaId, disciplinaId, dataInicio, dataFim));
    }

    private RelatorioFrequenciaResponse montarRelatorio(Long turmaId, Long disciplinaId,
                                                        LocalDate dataInicio, LocalDate dataFim) {
        TurmaReplica turma = turmaReplicaService.obter(turmaId);
        // Uma oferta já retirada continua valendo para consultar o histórico
        if (disciplinaId != null && turma.disciplina(disciplinaId).isEmpty()) {
            throw new RecursoNaoEncontradoException("Disciplina na turma " + turma.getCodigo(), disciplinaId);
        }
        LocalDate inicio = dataInicio != null ? dataInicio : LocalDate.of(turma.getAnoLetivo(), 1, 1);
        LocalDate fim = dataFim != null ? dataFim : LocalDate.of(turma.getAnoLetivo(), 12, 31);
        if (inicio.isAfter(fim)) {
            throw ValidacaoException.campo("dataFim", "deve ser igual ou posterior a dataInicio");
        }

        List<RelatorioFrequenciaResponse.Aluno> alunos = registroRepository.contarPorAluno(turmaId, disciplinaId, inicio, fim)
                .stream()
                .map(c -> {
                    ResumoFrequencia f = calculo.resumir(c.presencas(), c.faltas(), c.atrasos());
                    return new RelatorioFrequenciaResponse.Aluno(c.alunoId(), c.numeroMatricula(), c.nome(),
                            f.totalAulas(), f.presencas(), f.faltas(), f.atrasos(), f.percentual(), f.abaixoDoMinimo());
                })
                .toList();
        return new RelatorioFrequenciaResponse(turmaId, disciplinaId, inicio, fim, calculo.getMinimoPercentual(), alunos);
    }

    /** Todas as matrículas do aluno no ano letivo, inclusive encerradas (ex.: antes de uma transferência). */
    @Transactional(readOnly = true)
    public FrequenciaAlunoResponse porAluno(Long alunoId, Integer anoLetivo) {
        alunoService.obter(alunoId);
        List<FrequenciaAlunoResponse.Disciplina> disciplinas = registroRepository.contarPorDisciplina(alunoId, anoLetivo)
                .stream()
                .map(c -> new FrequenciaAlunoResponse.Disciplina(c.disciplinaId(), c.codigo(), c.nome(),
                        calculo.resumir(c.presencas(), c.faltas(), c.atrasos())))
                .toList();
        ResumoFrequencia geral = calculo.somar(disciplinas.stream().map(FrequenciaAlunoResponse.Disciplina::frequencia).toList());
        return new FrequenciaAlunoResponse(alunoId, anoLetivo, calculo.getMinimoPercentual(), disciplinas, geral);
    }
}
