package com.distrischool.school_core_service.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI schoolCoreOpenApi() {
        return new OpenAPI().info(new Info()
                .title("DistriSchool - school-core-service")
                .description("Gestão de alunos, professores, turmas e técnicos administrativos")
                .version("v1"));
    }
}
