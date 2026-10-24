package com.distrischool.gestao_administrativa.disciplina;

import com.distrischool.gestao_administrativa.common.RegraNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras de exclusão e de notificação das turmas; sem Spring. */
class DisciplinaServiceTest {

    private DisciplinaRepository disciplinaRepository;
    private ApplicationEventPublisher eventPublisher;
    private DisciplinaService disciplinaService;
    private Disciplina matematica;

    @BeforeEach
    void preparar() {
        disciplinaRepository = mock(DisciplinaRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        disciplinaService = new DisciplinaService(disciplinaRepository, eventPublisher);

        matematica = new Disciplina();
        matematica.setId(3L);
        matematica.setCodigo("MAT");
        matematica.setNome("Matemática");
        matematica.setCargaHoraria(80);

        when(disciplinaRepository.findById(3L)).thenReturn(Optional.of(matematica));
        when(disciplinaRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void renomearPublicaEventoParaAsTurmas() {
        disciplinaService.atualizar(3L, new DisciplinaRequest("MAT", "Matemática Básica", 80));

        verify(eventPublisher).publishEvent(new DisciplinaAlteradaEvent(3L));
        assertThat(matematica.getNome()).isEqualTo("Matemática Básica");
    }

    @Test
    void alterarOCodigoPublicaEventoParaAsTurmas() {
        disciplinaService.atualizar(3L, new DisciplinaRequest("MTB", "Matemática", 80));

        verify(eventPublisher).publishEvent(new DisciplinaAlteradaEvent(3L));
    }

    @Test
    void alterarSoACargaHorariaNaoNotificaAsTurmas() {
        disciplinaService.atualizar(3L, new DisciplinaRequest("MAT", "Matemática", 120));

        verify(eventPublisher, never()).publishEvent(any(DisciplinaAlteradaEvent.class));
        assertThat(matematica.getCargaHoraria()).isEqualTo(120);
    }

    @Test
    void atualizacaoRejeitadaPorCodigoDuplicadoNaoNotificaAsTurmas() {
        when(disciplinaRepository.existsByCodigoIgnoreCaseAndIdNot("POR", 3L)).thenReturn(true);

        assertThatThrownBy(() -> disciplinaService.atualizar(3L, new DisciplinaRequest("POR", "Matemática", 80)))
                .isInstanceOf(RegraNegocioException.class);

        verify(eventPublisher, never()).publishEvent(any(DisciplinaAlteradaEvent.class));
    }

    @Test
    void excluirDesativaADisciplinaNaoOfertada() {
        disciplinaService.excluir(3L);

        assertThat(matematica.isAtiva()).isFalse();
    }

    @Test
    void excluirDisciplinaOfertadaRetorna409EMantemAAtiva() {
        when(disciplinaRepository.estaOfertada(3L)).thenReturn(true);

        assertThatThrownBy(() -> disciplinaService.excluir(3L))
                .isInstanceOf(RegraNegocioException.class);

        assertThat(matematica.isAtiva()).isTrue();
    }
}
