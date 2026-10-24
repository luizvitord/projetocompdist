package com.distrischool.gestao_administrativa.tecnico;

import com.distrischool.gestao_administrativa.common.RecursoNaoEncontradoException;
import com.distrischool.gestao_administrativa.common.RegraNegocioException;
import com.distrischool.gestao_administrativa.event.StaffCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TecnicoService {

    // As mensagens não repetem o CPF informado (LGPD)
    private static final String CPF_DUPLICADO = "Já existe um técnico administrativo com este CPF";
    private static final String DUPLICADO = "Já existe um técnico administrativo com este CPF ou email";

    private final TecnicoRepository tecnicoRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public TecnicoResponse criar(TecnicoRequest request) {
        String cpf = somenteDigitos(request.cpf());
        if (tecnicoRepository.existsByCpf(cpf)) {
            throw new RegraNegocioException(CPF_DUPLICADO);
        }
        if (tecnicoRepository.existsByEmailIgnoreCase(request.email())) {
            throw new RegraNegocioException("Já existe um técnico administrativo com o email " + request.email());
        }
        Tecnico tecnico = new Tecnico();
        tecnico.setCpf(cpf);
        aplicar(tecnico, request);
        tecnico = salvar(tecnico);

        eventPublisher.publishEvent(new StaffCreatedEvent(
                tecnico.getId(), tecnico.getNome(), tecnico.getEmail(),
                tecnico.getCargo(), tecnico.getSetor(), Instant.now()));
        return TecnicoResponse.from(tecnico);
    }

    @Transactional(readOnly = true)
    public Page<TecnicoResponse> buscar(String nome, Cargo cargo, String setor, boolean ativo, Pageable pageable) {
        Specification<Tecnico> spec = TecnicoSpecifications.ativoIgual(ativo);
        if (StringUtils.hasText(nome)) {
            spec = spec.and(TecnicoSpecifications.nomeContem(nome.trim()));
        }
        if (cargo != null) {
            spec = spec.and(TecnicoSpecifications.cargoIgual(cargo));
        }
        if (StringUtils.hasText(setor)) {
            spec = spec.and(TecnicoSpecifications.setorIgual(setor.trim()));
        }
        return tecnicoRepository.findAll(spec, pageable).map(TecnicoResponse::from);
    }

    @Transactional(readOnly = true)
    public TecnicoResponse buscarPorId(Long id) {
        return TecnicoResponse.from(obter(id));
    }

    @Transactional
    public TecnicoResponse atualizar(Long id, TecnicoRequest request) {
        Tecnico tecnico = obter(id);
        String cpf = somenteDigitos(request.cpf());
        if (tecnicoRepository.existsByCpfAndIdNot(cpf, id)) {
            throw new RegraNegocioException(CPF_DUPLICADO);
        }
        if (tecnicoRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new RegraNegocioException("Já existe um técnico administrativo com o email " + request.email());
        }
        tecnico.setCpf(cpf);
        aplicar(tecnico, request);
        return TecnicoResponse.from(salvar(tecnico));
    }

    /** Exclusão lógica: preserva o registro para manter o histórico. */
    @Transactional
    public void excluir(Long id) {
        obter(id).setAtivo(false);
    }

    public Tecnico obter(Long id) {
        return tecnicoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Técnico administrativo", id));
    }

    /**
     * Força o flush para que uma violação de unicidade causada por cadastros concorrentes
     * (que passaram pelas verificações acima) vire 409 em vez de 500.
     */
    private Tecnico salvar(Tecnico tecnico) {
        try {
            return tecnicoRepository.saveAndFlush(tecnico);
        } catch (DataIntegrityViolationException ex) {
            throw new RegraNegocioException(DUPLICADO);
        }
    }

    private static String somenteDigitos(String cpf) {
        return cpf.replaceAll("\\D", "");
    }

    private static void aplicar(Tecnico tecnico, TecnicoRequest request) {
        tecnico.setNome(request.nome());
        tecnico.setCargo(request.cargo());
        tecnico.setSetor(request.setor());
        tecnico.setEmail(request.email());
        tecnico.setTelefone(request.telefone());
        tecnico.setDataAdmissao(request.dataAdmissao());
    }
}
