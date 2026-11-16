package com.distrischool.gestao_administrativa.professor;

import com.distrischool.gestao_administrativa.common.BuscaTexto;
import com.distrischool.gestao_administrativa.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_administrativa.common.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProfessorService {

    private final ProfessorRepository professorRepository;

    @Transactional
    public ProfessorResponse criar(ProfessorRequest request) {
        if (professorRepository.existsByEmailIgnoreCase(request.email())) {
            throw new RegraNegocioException("Já existe um professor com o email " + request.email());
        }
        Professor professor = new Professor();
        aplicar(professor, request);
        return ProfessorResponse.from(professorRepository.save(professor));
    }

    @Transactional(readOnly = true)
    public Page<ProfessorResponse> buscar(String nome, String busca, Pageable pageable) {
        Specification<Professor> spec = Specification.unrestricted();
        if (StringUtils.hasText(nome)) {
            spec = spec.and(ProfessorSpecifications.nomeContem(nome.trim()));
        }
        String termo = BuscaTexto.normalizar(busca);
        if (termo != null) {
            spec = spec.and(ProfessorSpecifications.busca(termo));
        }
        return professorRepository.findAll(spec, pageable).map(ProfessorResponse::from);
    }

    @Transactional(readOnly = true)
    public ProfessorResponse buscarPorId(Long id) {
        return ProfessorResponse.from(obter(id));
    }

    @Transactional
    public ProfessorResponse atualizar(Long id, ProfessorRequest request) {
        Professor professor = obter(id);
        if (professorRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new RegraNegocioException("Já existe um professor com o email " + request.email());
        }
        aplicar(professor, request);
        return ProfessorResponse.from(professor);
    }

    public Professor obter(Long id) {
        return professorRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Professor", id));
    }

    private static void aplicar(Professor professor, ProfessorRequest request) {
        professor.setNome(request.nome());
        professor.setQualificacao(request.qualificacao());
        professor.setEmail(request.email());
        professor.setTelefone(request.telefone());
    }
}
