package com.distrischool.api_gateway.config;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decide as consultas ({@code GET /api/**}) pelo perfil do token.
 * <ul>
 *   <li>ADMIN e PROFESSOR: qualquer consulta.</li>
 *   <li>ALUNO e RESPONSAVEL: só os dados do aluno ligado à conta (claim {@code referenciaId}),
 *       numa lista fechada de rotas. Todo o resto é negado.</li>
 *   <li>Qualquer outro perfil (ou token sem perfil): negado.</li>
 * </ul>
 * O id do caminho é comparado como número inteiro exato, no caminho como chegou
 * ({@code getRequestURI}, sem decodificar). O firewall do Spring Security já recusou antes
 * caminhos com {@code ..}, {@code //}, {@code ;} ou {@code %2F}/{@code %2E}; aqui o caminho
 * precisa casar inteiro com a rota, então barra final, zeros à esquerda ({@code 012}), sufixos
 * ({@code 12abc}) e dígitos codificados ({@code %31%32}) não passam.
 */
final class LeituraPorPerfilAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final AuthorizationDecision PERMITIDO = new AuthorizationDecision(true);
    private static final AuthorizationDecision NEGADO = new AuthorizationDecision(false);

    /** Inteiro positivo sem zeros à esquerda; até 19 dígitos (o parse ainda pode estourar o long). */
    private static final String ID = "([1-9][0-9]{0,18})";

    /** Rotas em que o id do caminho precisa ser o do aluno do token. */
    private static final Pattern[] ROTAS_DO_ALUNO = {
            Pattern.compile("/api/alunos/" + ID),
            Pattern.compile("/api/alunos/" + ID + "/boletim"),
            Pattern.compile("/api/frequencias/alunos/" + ID),
    };

    private static final String MATRICULAS = "/api/matriculas";
    private static final String ALUNO_ID = "alunoId";
    /**
     * Parâmetros aceitos na listagem de matrículas, além do {@code alunoId}. O serviço combina os
     * filtros com E, então nenhum deles amplia o resultado para outros alunos; a paginação também
     * é aceita.
     */
    private static final Set<String> PARAMETROS_MATRICULAS = Set.of(ALUNO_ID, "anoLetivo", "status", "page", "size", "sort");

    @Override
    public AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication,
                                         RequestAuthorizationContext contexto) {
        if (!(authentication.get() instanceof JwtAuthenticationToken token)) {
            return NEGADO;
        }
        String perfil = token.getToken().getClaimAsString("perfil");
        if ("ADMIN".equals(perfil) || "PROFESSOR".equals(perfil)) {
            return PERMITIDO;
        }
        if ("ALUNO".equals(perfil) || "RESPONSAVEL".equals(perfil)) {
            Long alunoId = referenciaId(token.getToken().getClaim("referenciaId"));
            return new AuthorizationDecision(alunoId != null && dadosDoAluno(contexto.getRequest(), alunoId));
        }
        return NEGADO;
    }

    /** O usuarios-service grava o {@code referenciaId} como número; outro tipo é recusado. */
    private static @Nullable Long referenciaId(@Nullable Object claim) {
        if (claim instanceof Long valor) {
            return valor;
        }
        if (claim instanceof Integer valor) {
            return valor.longValue();
        }
        return null;
    }

    private static boolean dadosDoAluno(HttpServletRequest request, long alunoId) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        for (Pattern rota : ROTAS_DO_ALUNO) {
            Matcher casamento = rota.matcher(caminho);
            if (casamento.matches()) {
                return mesmoId(casamento.group(1), alunoId);
            }
        }
        if (MATRICULAS.equals(caminho)) {
            return matriculasDoAluno(request.getParameterMap(), alunoId);
        }
        return false;
    }

    /** {@code alunoId} presente uma única vez, igual ao do token, e só parâmetros conhecidos. */
    private static boolean matriculasDoAluno(Map<String, String[]> parametros, long alunoId) {
        if (!PARAMETROS_MATRICULAS.containsAll(parametros.keySet())) {
            return false;
        }
        String[] valores = parametros.get(ALUNO_ID);
        return valores != null && valores.length == 1
                && valores[0].matches(ID) && mesmoId(valores[0], alunoId);
    }

    private static boolean mesmoId(String texto, long alunoId) {
        try {
            return Long.parseLong(texto) == alunoId;
        } catch (NumberFormatException estourou) {
            return false;
        }
    }
}
