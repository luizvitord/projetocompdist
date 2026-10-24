package com.distrischool.gestao_escolar.frequencia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class FrequenciaConfig {

    @Bean
    CalculoFrequencia calculoFrequencia(@Value("${escolar.frequencia.minima-percentual:75}") BigDecimal minimo) {
        return new CalculoFrequencia(minimo);
    }
}
