package com.distrischool.notificacao.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer";

    /** Todas as rotas exigem o JWT do login; o botão "Authorize" do Swagger UI envia o token. */
    @Bean
    OpenAPI notificacaoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DistriSchool - notificacao-service")
                        .description("Notificações e avisos gerados a partir dos eventos dos outros serviços")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
