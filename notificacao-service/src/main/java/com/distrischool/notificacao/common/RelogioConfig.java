package com.distrischool.notificacao.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Relógio injetável: os testes fixam o horário de criação e de leitura das notificações. */
@Configuration
public class RelogioConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
