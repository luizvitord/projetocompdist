package com.distrischool.api_gateway.config;

import com.distrischool.api_gateway.resiliencia.IndisponibilidadeFilter;
import com.distrischool.api_gateway.resiliencia.LoginRateLimitFilter;
import com.distrischool.api_gateway.resiliencia.ResilienciaProperties;
import com.distrischool.api_gateway.resiliencia.RetryGetFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * Rotas para os serviços de trás. Cada rota passa, de fora para dentro, por:
 * <ol>
 *   <li>{@link IndisponibilidadeFilter}: circuito aberto, erro de conexão ou timeout viram 503 com ProblemDetail;</li>
 *   <li>circuit breaker Resilience4j com o id da rota ({@code resilience4j.circuitbreaker.instances.<id>});</li>
 *   <li>{@link RetryGetFilter}: novas tentativas só para GET;</li>
 *   <li>o proxy HTTP, com os timeouts de {@code spring.http.clients.*}.</li>
 * </ol>
 * O login ainda passa antes pelo {@link LoginRateLimitFilter}, para que tentativas bloqueadas não
 * cheguem ao usuarios-service nem contem no circuito.
 */
@Configuration
public class GatewayConfig {

    @Bean
    RouterFunction<ServerResponse> escolarRoutes(GatewayRoutesProperties properties, ResilienciaProperties resiliencia) {
        return route("gestao-escolar")
                .route(path("/api/alunos/**")
                                .or(path("/api/matriculas/**"))
                                .or(path("/api/avaliacoes/**"))
                                .or(path("/api/notas/**"))
                                .or(path("/api/frequencias/**"))
                                .or(path("/escolar/**")), http())
                .before(uri(properties.escolarUrl()))
                .filter(resiliente("gestao-escolar", resiliencia))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> administrativaRoutes(GatewayRoutesProperties properties, ResilienciaProperties resiliencia) {
        return route("gestao-administrativa")
                .route(path("/api/turmas/**")
                                .or(path("/api/disciplinas/**"))
                                .or(path("/api/professores/**"))
                                .or(path("/api/tecnicos/**"))
                                .or(path("/administrativa/**")), http())
                .before(uri(properties.administrativaUrl()))
                .filter(resiliente("gestao-administrativa", resiliencia))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> usuariosRoutes(GatewayRoutesProperties properties, ResilienciaProperties resiliencia) {
        return route("usuarios")
                .route(path("/api/auth/**")
                                .or(path("/api/usuarios/**"))
                                .or(path("/usuarios/**")), http())
                .before(uri(properties.usuariosUrl()))
                .filter(new LoginRateLimitFilter(resiliencia.login()).andThen(resiliente("usuarios", resiliencia)))
                .build();
    }

    private static HandlerFilterFunction<ServerResponse, ServerResponse> resiliente(String id, ResilienciaProperties resiliencia) {
        return new IndisponibilidadeFilter(id)
                .andThen(circuitBreaker(id))
                .andThen(new RetryGetFilter(resiliencia.retry()));
    }
}
