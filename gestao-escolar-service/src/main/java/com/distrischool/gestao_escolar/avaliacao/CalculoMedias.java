package com.distrischool.gestao_escolar.avaliacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Cálculo de médias, sem dependência do Spring:
 * <ul>
 *   <li>toda nota é normalizada para a escala 0–10 ({@code valor / notaMaxima × 10});</li>
 *   <li>média do bimestre: média ponderada pelo peso das avaliações que não são recuperação;
 *       com recuperação, vale a maior entre essa média e a nota da recuperação;</li>
 *   <li>avaliação sem nota não entra no cálculo e é contada como pendente;</li>
 *   <li>média anual: média aritmética das médias (já arredondadas) dos bimestres que têm média,
 *       para que o boletim possa ser conferido a partir dos valores que ele mostra;</li>
 *   <li>resultados em {@link BigDecimal} com 2 casas, {@code HALF_UP}; os passos intermediários
 *       não são arredondados.</li>
 * </ul>
 */
public final class CalculoMedias {

    public static final int BIMESTRES = 4;

    private static final int ESCALA = 2;
    private static final int ESCALA_INTERMEDIARIA = 10;
    private static final BigDecimal DEZ = BigDecimal.TEN;

    private final BigDecimal mediaAprovacao;

    public CalculoMedias(BigDecimal mediaAprovacao) {
        if (mediaAprovacao == null || mediaAprovacao.signum() < 0 || mediaAprovacao.compareTo(DEZ) > 0) {
            throw new IllegalArgumentException("Média de aprovação deve estar entre 0 e 10: " + mediaAprovacao);
        }
        this.mediaAprovacao = mediaAprovacao;
    }

    /** Uma avaliação do aluno; {@code valor} é {@code null} quando a nota ainda não foi lançada. */
    public record NotaAvaliacao(int bimestre, TipoAvaliacao tipo, BigDecimal peso, BigDecimal notaMaxima,
                                BigDecimal valor) {

        public NotaAvaliacao {
            Objects.requireNonNull(tipo, "tipo");
            if (bimestre < 1 || bimestre > BIMESTRES) {
                throw new IllegalArgumentException("Bimestre deve estar entre 1 e 4: " + bimestre);
            }
            if (peso == null || peso.signum() <= 0) {
                throw new IllegalArgumentException("Peso deve ser positivo: " + peso);
            }
            if (notaMaxima == null || notaMaxima.signum() <= 0) {
                throw new IllegalArgumentException("Nota máxima deve ser positiva: " + notaMaxima);
            }
            if (valor != null && (valor.signum() < 0 || valor.compareTo(notaMaxima) > 0)) {
                throw new IllegalArgumentException("Nota fora da faixa 0.." + notaMaxima + ": " + valor);
            }
        }

        boolean lancada() {
            return valor != null;
        }
    }

    /**
     * @param media                 média final do bimestre ({@code null} se nenhuma nota foi lançada)
     * @param mediaSemRecuperacao   média ponderada das avaliações regulares
     * @param recuperacao           nota da recuperação, normalizada ({@code null} se não houver)
     */
    public record MediaBimestre(int bimestre, BigDecimal media, BigDecimal mediaSemRecuperacao,
                                BigDecimal recuperacao, int avaliacoesPendentes) {
    }

    public record ResultadoDisciplina(List<MediaBimestre> bimestres, BigDecimal mediaAnual,
                                      int avaliacoesPendentes, boolean atingiuMedia) {
    }

    public BigDecimal getMediaAprovacao() {
        return mediaAprovacao;
    }

    /** Médias dos 4 bimestres (sempre 4 itens, em ordem) e média anual de uma disciplina. */
    public ResultadoDisciplina calcular(Collection<NotaAvaliacao> notas) {
        List<MediaBimestre> bimestres = new ArrayList<>(BIMESTRES);
        for (int b = 1; b <= BIMESTRES; b++) {
            int bimestre = b;
            bimestres.add(mediaDoBimestre(bimestre, notas.stream().filter(n -> n.bimestre() == bimestre).toList()));
        }
        BigDecimal mediaAnual = mediaAnual(bimestres);
        int pendentes = bimestres.stream().mapToInt(MediaBimestre::avaliacoesPendentes).sum();
        return new ResultadoDisciplina(bimestres, mediaAnual, pendentes, atingiu(mediaAnual));
    }

    MediaBimestre mediaDoBimestre(int bimestre, List<NotaAvaliacao> doBimestre) {
        BigDecimal somaPonderada = BigDecimal.ZERO;
        BigDecimal somaPesos = BigDecimal.ZERO;
        BigDecimal recuperacao = null;
        int pendentes = 0;
        for (NotaAvaliacao nota : doBimestre) {
            if (!nota.lancada()) {
                pendentes++;
            } else if (nota.tipo() == TipoAvaliacao.RECUPERACAO) {
                recuperacao = maior(recuperacao, normalizar(nota.valor(), nota.notaMaxima()));
            } else {
                somaPonderada = somaPonderada.add(normalizar(nota.valor(), nota.notaMaxima()).multiply(nota.peso()));
                somaPesos = somaPesos.add(nota.peso());
            }
        }
        BigDecimal semRecuperacao = somaPesos.signum() == 0 ? null
                : somaPonderada.divide(somaPesos, ESCALA_INTERMEDIARIA, RoundingMode.HALF_UP);
        BigDecimal media = maior(semRecuperacao, recuperacao);
        return new MediaBimestre(bimestre, arredondar(media), arredondar(semRecuperacao), arredondar(recuperacao), pendentes);
    }

    BigDecimal mediaAnual(List<MediaBimestre> bimestres) {
        List<BigDecimal> medias = bimestres.stream().map(MediaBimestre::media).filter(Objects::nonNull).toList();
        if (medias.isEmpty()) {
            return null;
        }
        BigDecimal soma = medias.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return soma.divide(BigDecimal.valueOf(medias.size()), ESCALA, RoundingMode.HALF_UP);
    }

    public boolean atingiu(BigDecimal media) {
        return media != null && media.compareTo(mediaAprovacao) >= 0;
    }

    static BigDecimal normalizar(BigDecimal valor, BigDecimal notaMaxima) {
        return valor.multiply(DEZ).divide(notaMaxima, ESCALA_INTERMEDIARIA, RoundingMode.HALF_UP);
    }

    private static BigDecimal maior(BigDecimal a, BigDecimal b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.max(b);
    }

    private static BigDecimal arredondar(BigDecimal valor) {
        return valor == null ? null : valor.setScale(ESCALA, RoundingMode.HALF_UP);
    }
}
