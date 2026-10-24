package com.distrischool.gestao_administrativa.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI gestaoAdministrativaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("DistriSchool - gestao-administrativa-service")
                .description("Gestão de professores, turmas e técnicos administrativos")
                .version("v1"));
    }
}
