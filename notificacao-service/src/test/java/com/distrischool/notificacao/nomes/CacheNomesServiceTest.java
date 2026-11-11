package com.distrischool.notificacao.nomes;

import com.distrischool.notificacao.common.MensagemInvalidaException;
import com.distrischool.notificacao.nomes.ScheduleUpdatedMessage.DisciplinaOfertada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Cache de nomes (idempotência e ordem dos eventos); sem Spring, com as tabelas em memória. */
class CacheNomesServiceTest {

    private static final Instant T1 = Instant.parse("2026-10-24T11:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-24T11:05:00Z");
    private static final Instant T3 = Instant.parse("2026-10-24T11:10:00Z");

    private final Map<Long, TurmaNome> turmas = new HashMap<>();
    private final Map<Long, DisciplinaNome> disciplinas = new HashMap<>();
    /** Ids das disciplinas na ordem em que foram bloqueadas ({@code SELECT ... FOR UPDATE}). */
    private final List<Long> disciplinasBloqueadas = new ArrayList<>();
    private CacheNomesService service;

    @BeforeEach
    void preparar() {
        TurmaNomeRepository turmaRepository = mock(TurmaNomeRepository.class);
        when(turmaRepository.findByIdParaAtualizacao(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(turmas.get(inv.<Long>getArgument(0))));
        when(turmaRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(turmas.get(inv.<Long>getArgument(0))));
        when(turmaRepository.saveAndFlush(any())).thenAnswer(inv -> {
            TurmaNome turma = inv.getArgument(0);
            turma.marcarComoExistente();
            turmas.put(turma.getId(), turma);
            return turma;
        });

        DisciplinaNomeRepository disciplinaRepository = mock(DisciplinaNomeRepository.class);
        when(disciplinaRepository.findByIdParaAtualizacao(anyLong())).thenAnswer(inv -> {
            disciplinasBloqueadas.add(inv.getArgument(0));
            return Optional.ofNullable(disciplinas.get(inv.<Long>getArgument(0)));
        });
        when(disciplinaRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(disciplinas.get(inv.<Long>getArgument(0))));
        when(disciplinaRepository.saveAndFlush(any())).thenAnswer(inv -> {
            DisciplinaNome disciplina = inv.getArgument(0);
            disciplina.marcarComoExistente();
            disciplinas.put(disciplina.getId(), disciplina);
            return disciplina;
        });

        service = new CacheNomesService(turmaRepository, disciplinaRepository);
    }

    private static ScheduleUpdatedMessage evento(Long turmaId, String nomeTurma, Instant occurredAt,
                                                 DisciplinaOfertada... ofertas) {
        return new ScheduleUpdatedMessage(turmaId, "9A-2026", nomeTurma, List.of(ofertas), occurredAt);
    }

    private static DisciplinaOfertada oferta(Long id, String nome) {
        return new DisciplinaOfertada(id, nome);
    }

    @Test
    void primeiroEventoGravaTurmaEDisciplinas() {
        assertThat(service.aplicar(evento(12L, "9º Ano A", T1, oferta(3L, "Matemática"), oferta(4L, "Português"))))
                .isTrue();

        assertThat(service.nomeTurma(12L)).contains("9º Ano A");
        assertThat(turmas.get(12L).getCodigo()).isEqualTo("9A-2026");
        assertThat(turmas.get(12L).getUltimoEventoEm()).isEqualTo(T1);
        assertThat(service.nomeDisciplina(3L)).contains("Matemática");
        assertThat(service.nomeDisciplina(4L)).contains("Português");
    }

    @Test
    void nomeDesconhecidoFicaVazio() {
        assertThat(service.nomeTurma(99L)).isEmpty();
        assertThat(service.nomeDisciplina(99L)).isEmpty();
    }

    @Test
    void eventoMaisNovoAtualizaOsNomes() {
        service.aplicar(evento(12L, "9º Ano A", T1, oferta(3L, "Matemática")));

        assertThat(service.aplicar(evento(12L, "9º Ano A - Manhã", T2, oferta(3L, "Matemática I")))).isTrue();

        assertThat(service.nomeTurma(12L)).contains("9º Ano A - Manhã");
        assertThat(turmas.get(12L).getUltimoEventoEm()).isEqualTo(T2);
        assertThat(service.nomeDisciplina(3L)).contains("Matemática I");
    }

    @Test
    void eventoAntigoEIgnorado() {
        service.aplicar(evento(12L, "9º Ano A - Manhã", T2, oferta(3L, "Matemática I")));

        // Entrega fora de ordem: o evento de T1 chega depois do de T2
        assertThat(service.aplicar(evento(12L, "9º Ano A", T1, oferta(3L, "Matemática"), oferta(4L, "Português"))))
                .isFalse();

        assertThat(service.nomeTurma(12L)).contains("9º Ano A - Manhã");
        assertThat(turmas.get(12L).getUltimoEventoEm()).isEqualTo(T2);
        assertThat(service.nomeDisciplina(3L)).contains("Matemática I");
        // Nem as disciplinas do evento antigo são aplicadas
        assertThat(service.nomeDisciplina(4L)).isEmpty();
    }

    @Test
    void reentregaDoMesmoEventoEAplicadaSemEfeito() {
        ScheduleUpdatedMessage evento = evento(12L, "9º Ano A", T1, oferta(3L, "Matemática"));
        service.aplicar(evento);

        assertThat(service.aplicar(evento)).isTrue();

        assertThat(turmas).hasSize(1);
        assertThat(disciplinas).hasSize(1);
        assertThat(service.nomeTurma(12L)).contains("9º Ano A");
    }

    @Test
    void occurredAtComNanossegundosETruncadoComoNoBanco() {
        // O TIMESTAMPTZ guarda microssegundos; a reentrega do mesmo evento não pode parecer antiga
        Instant comNanos = Instant.parse("2026-10-24T11:00:00.123456789Z");
        service.aplicar(evento(12L, "9º Ano A", comNanos));

        assertThat(turmas.get(12L).getUltimoEventoEm()).isEqualTo(Instant.parse("2026-10-24T11:00:00.123456Z"));
        assertThat(service.aplicar(evento(12L, "9º Ano A", comNanos))).isTrue();
    }

    @Test
    void disciplinaMantemONomeMaisNovoVindoDeOutraTurma() {
        // A turma 13 trouxe o nome novo da disciplina em T3
        service.aplicar(evento(13L, "9º Ano B", T3, oferta(3L, "Matemática Aplicada")));

        // Evento da turma 12 em T2: novo para a turma, mas mais antigo que o nome da disciplina
        assertThat(service.aplicar(evento(12L, "9º Ano A", T2, oferta(3L, "Matemática"), oferta(4L, "Português"))))
                .isTrue();

        assertThat(service.nomeTurma(12L)).contains("9º Ano A");
        assertThat(service.nomeDisciplina(3L)).contains("Matemática Aplicada");
        assertThat(disciplinas.get(3L).getUltimoEventoEm()).isEqualTo(T3);
        assertThat(service.nomeDisciplina(4L)).contains("Português");
    }

    @Test
    void disciplinasSaoBloqueadasSempreNaOrdemDosIds() {
        service.aplicar(evento(12L, "9º Ano A", T1, oferta(9L, "História"), oferta(3L, "Matemática"),
                oferta(5L, "Ciências")));

        assertThat(disciplinasBloqueadas).containsExactly(3L, 5L, 9L);
    }

    @Test
    void turmaSemDisciplinasSoGravaATurma() {
        assertThat(service.aplicar(evento(12L, "9º Ano A", T1))).isTrue();

        assertThat(service.nomeTurma(12L)).contains("9º Ano A");
        assertThat(disciplinas).isEmpty();
    }

    @Test
    void eventoForaDoContratoLancaMensagemInvalida() {
        assertThatThrownBy(() -> service.aplicar(null)).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> service.aplicar(evento(null, "9º Ano A", T1)))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> service.aplicar(evento(12L, null, T1)))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> service.aplicar(evento(12L, "9º Ano A", null)))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> service.aplicar(new ScheduleUpdatedMessage(12L, "9A-2026", "9º Ano A", null, T1)))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> service.aplicar(evento(12L, "9º Ano A", T1, oferta(null, "Matemática"))))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> service.aplicar(evento(12L, "9º Ano A", T1, oferta(3L, null))))
                .isInstanceOf(MensagemInvalidaException.class);

        assertThat(turmas).isEmpty();
        assertThat(disciplinas).isEmpty();
    }
}
