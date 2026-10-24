package com.distrischool.gestao_administrativa.disciplina;

import com.distrischool.gestao_administrativa.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_administrativa.common.RegraNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DisciplinaService {

    private final DisciplinaRepository disciplinaRepository;

    @Transactional
    public DisciplinaResponse criar(DisciplinaRequest request) {
        if (disciplinaRepository.existsByCodigoIgnoreCase(request.codigo())) {
            throw new RegraNegocioException("Já existe uma disciplina com o código " + request.codigo());
        }
        Disciplina disciplina = new Disciplina();
        aplicar(disciplina, request);
        return DisciplinaResponse.from(salvar(disciplina, request.codigo()));
    }

    @Transactional(readOnly = true)
    public Page<DisciplinaResponse> buscar(String nome, boolean ativa, Pageable pageable) {
        Specification<Disciplina> spec = DisciplinaSpecifications.ativaIgual(ativa);
        if (StringUtils.hasText(nome)) {
            spec = spec.and(DisciplinaSpecifications.nomeContem(nome.trim()));
        }
        return disciplinaRepository.findAll(spec, pageable).map(DisciplinaResponse::from);
    }

    @Transactional(readOnly = true)
    public DisciplinaResponse buscarPorId(Long id) {
        return DisciplinaResponse.from(obter(id));
    }

    @Transactional
    public DisciplinaResponse atualizar(Long id, DisciplinaRequest request) {
        Disciplina disciplina = obter(id);
        if (disciplinaRepository.existsByCodigoIgnoreCaseAndIdNot(request.codigo(), id)) {
            throw new RegraNegocioException("Já existe uma disciplina com o código " + request.codigo());
        }
        aplicar(disciplina, request);
        return DisciplinaResponse.from(salvar(disciplina, request.codigo()));
    }

    /** Exclusão lógica: a disciplina some das listagens, mas o registro é preservado. */
    @Transactional
    public void excluir(Long id) {
        Disciplina disciplina = obter(id);
        if (disciplinaRepository.estaOfertada(id)) {
            throw new RegraNegocioException("A disciplina " + disciplina.getCodigo()
                    + " está ofertada em uma ou mais turmas; retire a oferta antes de excluí-la");
        }
        disciplina.setAtiva(false);
    }

    public Disciplina obter(Long id) {
        return disciplinaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Disciplina", id));
    }

    /**
     * Força o flush para que uma violação de unicidade causada por cadastros concorrentes
     * vire 409 em vez de 500.
     */
    private Disciplina salvar(Disciplina disciplina, String codigo) {
        try {
            return disciplinaRepository.saveAndFlush(disciplina);
        } catch (DataIntegrityViolationException ex) {
            throw new RegraNegocioException("Já existe uma disciplina com o código " + codigo);
        }
    }

    private static void aplicar(Disciplina disciplina, DisciplinaRequest request) {
        disciplina.setCodigo(request.codigo());
        disciplina.setNome(request.nome());
        disciplina.setCargaHoraria(request.cargaHoraria());
    }
}
