package com.distrischool.gestao_escolar.common;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;

import java.util.Arrays;
import java.util.Locale;

/**
 * Parâmetro {@code busca} das listagens: procura o texto em vários campos ao mesmo tempo, sem
 * diferenciar maiúsculas nem acentos ("avaliacao" encontra "Avaliação").
 * <p>
 * Os acentos são removidos pela função {@code unaccent} do PostgreSQL (extensão criada por
 * migration), nos dois lados da comparação, para que a regra seja exatamente a mesma no campo e no
 * texto digitado (inclusive para "º" e "ª"). {@code %} e {@code _} digitados valem como texto.
 */
public final class BuscaTexto {

    /** Tamanho máximo aceito pelos controllers ({@code @Size}); acima disso, 400. */
    public static final int TAMANHO_MAXIMO = 100;
    public static final String MENSAGEM_TAMANHO = "deve ter no máximo " + TAMANHO_MAXIMO + " caracteres";

    private static final char ESCAPE = '\\';

    private BuscaTexto() {
    }

    /** Texto sem espaços nas pontas, ou {@code null} quando vazio ou só com espaços (sem filtro). */
    public static String normalizar(String busca) {
        if (busca == null) {
            return null;
        }
        String texto = busca.strip();
        return texto.isEmpty() ? null : texto;
    }

    /** Verdadeiro quando algum dos campos contém o texto. */
    @SafeVarargs
    public static Predicate algumContem(CriteriaBuilder cb, String busca, Expression<String>... campos) {
        // value() vira parâmetro (?) na SQL; cb.literal() colocaria o texto digitado dentro da SQL
        Expression<String> padrao = semAcentoMinusculo(cb, ((HibernateCriteriaBuilder) cb).value(padraoLike(busca)));
        return cb.or(Arrays.stream(campos)
                .map(campo -> cb.like(semAcentoMinusculo(cb, campo), padrao, ESCAPE))
                .toArray(Predicate[]::new));
    }

    static String padraoLike(String busca) {
        String escapado = busca.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escapado + "%";
    }

    private static Expression<String> semAcentoMinusculo(CriteriaBuilder cb, Expression<String> valor) {
        return cb.lower(cb.function("unaccent", String.class, valor));
    }
}
