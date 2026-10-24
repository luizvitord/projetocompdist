package com.distrischool.gestao_escolar.avaliacao;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class AvaliacaoConfig {

    @Bean
    CalculoMedias calculoMedias(@Value("${escolar.avaliacao.media-aprovacao:6.0}") BigDecimal mediaAprovacao) {
        return new CalculoMedias(mediaAprovacao);
    }
}
