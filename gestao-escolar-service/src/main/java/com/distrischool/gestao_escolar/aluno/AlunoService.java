package com.distrischool.gestao_escolar.aluno;

import com.distrischool.gestao_escolar.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_escolar.event.StudentCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.Year;

@Service
@RequiredArgsConstructor
public class AlunoService {

    private final AlunoRepository alunoRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AlunoResponse criar(AlunoRequest request) {
        Aluno aluno = new Aluno();
        aluno.setMatricula(gerarMatricula());
        aplicar(aluno, request);
        aluno = alunoRepository.save(aluno);

        eventPublisher.publishEvent(new StudentCreatedEvent(
                aluno.getId(), aluno.getMatricula(), aluno.getNome(), aluno.getEmail(), Instant.now()));
        return AlunoResponse.from(aluno);
    }

    @Transactional(readOnly = true)
    public Page<AlunoResponse> buscar(String nome, String matricula, Pageable pageable) {
        Specification<Aluno> spec = Specification.unrestricted();
        if (StringUtils.hasText(nome)) {
            spec = spec.and(AlunoSpecifications.nomeContem(nome.trim()));
        }
        if (StringUtils.hasText(matricula)) {
            spec = spec.and(AlunoSpecifications.matriculaIgual(matricula.trim()));
        }
        return alunoRepository.findAll(spec, pageable).map(AlunoResponse::from);
    }

    @Transactional(readOnly = true)
    public AlunoResponse buscarPorId(Long id) {
        return AlunoResponse.from(obter(id));
    }

    @Transactional
    public AlunoResponse atualizar(Long id, AlunoRequest request) {
        Aluno aluno = obter(id);
        aplicar(aluno, request);
        return AlunoResponse.from(aluno);
    }

    @Transactional
    public void excluir(Long id) {
        alunoRepository.delete(obter(id));
    }

    public Aluno obter(Long id) {
        return alunoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno", id));
    }

    private String gerarMatricula() {
        long sequencial = alunoRepository.nextMatriculaSequence();
        return "%d%06d".formatted(Year.now().getValue(), sequencial);
    }

    private static void aplicar(Aluno aluno, AlunoRequest request) {
        aluno.setNome(request.nome());
        aluno.setDataNascimento(request.dataNascimento());
        aluno.setEndereco(request.endereco());
        aluno.setTelefone(request.telefone());
        aluno.setEmail(request.email());
    }
}
