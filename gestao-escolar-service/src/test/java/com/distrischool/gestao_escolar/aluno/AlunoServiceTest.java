package com.distrischool.gestao_escolar.aluno;

import com.distrischool.gestao_escolar.event.StudentCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cadastro de alunos; sem Spring, com repositório simulado. */
class AlunoServiceTest {

    private AlunoRepository alunoRepository;
    private ApplicationEventPublisher eventPublisher;
    private AlunoService alunoService;

    @BeforeEach
    void preparar() {
        alunoRepository = mock(AlunoRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        alunoService = new AlunoService(alunoRepository, eventPublisher);

        when(alunoRepository.save(any())).thenAnswer(inv -> {
            Aluno a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });
    }

    @Test
    void geraNumeroDeMatriculaComAnoESeisDigitos() {
        when(alunoRepository.nextMatriculaSequence()).thenReturn(42L);

        AlunoResponse resposta = alunoService.criar(request());

        assertThat(resposta.matricula()).isEqualTo(Year.now().getValue() + "000042");
    }

    @Test
    void publicaStudentCreatedComOMesmoPayloadDeAntes() {
        when(alunoRepository.nextMatriculaSequence()).thenReturn(1L);

        alunoService.criar(request());

        ArgumentCaptor<StudentCreatedEvent> captor = ArgumentCaptor.forClass(StudentCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        StudentCreatedEvent evento = captor.getValue();
        assertThat(evento.alunoId()).isEqualTo(1L);
        assertThat(evento.matricula()).isEqualTo(Year.now().getValue() + "000001");
        assertThat(evento.nome()).isEqualTo("Maria Silva");
        assertThat(evento.email()).isEqualTo("maria@example.com");
        assertThat(evento.occurredAt()).isNotNull();
    }

    private static AlunoRequest request() {
        return new AlunoRequest("Maria Silva", LocalDate.of(2012, 3, 10),
                "Rua A, 10", "11999990000", "maria@example.com");
    }
}
