package com.distrischool.usuarios.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI usuariosOpenApi() {
        return new OpenAPI().info(new Info()
                .title("DistriSchool - usuarios-service")
                .description("Usuários, perfis de acesso e login")
                .version("v1"));
    }
}
