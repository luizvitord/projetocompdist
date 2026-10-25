package com.distrischool.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

@Configuration
public class GatewayConfig {

    @Bean
    RouterFunction<ServerResponse> escolarRoutes(GatewayRoutesProperties properties) {
        return route("gestao-escolar")
                .route(path("/api/alunos/**")
                                .or(path("/api/matriculas/**"))
                                .or(path("/api/avaliacoes/**"))
                                .or(path("/api/notas/**"))
                                .or(path("/api/frequencias/**"))
                                .or(path("/escolar/**")), http())
                .before(uri(properties.escolarUrl()))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> administrativaRoutes(GatewayRoutesProperties properties) {
        return route("gestao-administrativa")
                .route(path("/api/turmas/**")
                                .or(path("/api/disciplinas/**"))
                                .or(path("/api/professores/**"))
                                .or(path("/api/tecnicos/**"))
                                .or(path("/administrativa/**")), http())
                .before(uri(properties.administrativaUrl()))
                .build();
    }

    @Bean
    WebMvcConfigurer corsConfigurer(GatewayRoutesProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(properties.allowedOrigin())
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
