package com.distrischool.gestao_escolar.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI gestaoEscolarOpenApi() {
        return new OpenAPI().info(new Info()
                .title("DistriSchool - gestao-escolar-service")
                .description("Gestão de alunos, matrículas, frequências e notas")
                .version("v1"));
    }
}
