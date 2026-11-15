package com.distrischool.api_gateway.config;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * O gateway é a porta de entrada: toda requisição para /api precisa de um JWT válido, emitido
 * pelo usuarios-service no login. Os serviços de trás confiam no gateway e não validam o token;
 * a exceção é o notificacao-service, que também valida o token para identificar o destinatário.
 * <p>
 * Regras por perfil (claim {@code perfil} do token), na ordem em que são avaliadas:
 * <ul>
 *   <li>login, health checks e Swagger: livres;</li>
 *   <li>administração de usuários: só ADMIN;</li>
 *   <li>notificações do próprio usuário (todos os métodos): qualquer usuário autenticado;</li>
 *   <li>consultas (GET): ADMIN e PROFESSOR, qualquer uma; ALUNO e RESPONSAVEL, só os dados do aluno
 *       ligado à conta ({@link LeituraPorPerfilAuthorizationManager});</li>
 *   <li>lançar notas e frequência: ADMIN ou PROFESSOR;</li>
 *   <li>demais alterações (cadastros, turmas, matrículas): só ADMIN.</li>
 * </ul>
 * O 403 sai como ProblemDetail ({@link AcessoNegadoHandler}).
 */
@Configuration
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String PROFESSOR = "PROFESSOR";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AcessoNegadoHandler acessoNegado) throws Exception {
        http
                // API sem sessão nem cookies: o token vai em cada requisição, então não há CSRF
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        // Swagger de cada serviço
                        .requestMatchers("/administrativa/**", "/escolar/**", "/usuarios/**", "/notificacao/**")
                                .permitAll()

                        .requestMatchers("/api/usuarios/**").hasRole(ADMIN)
                        // Antes da regra que restringe /api/** a ADMIN: cada usuário lê e marca como
                        // lidas as próprias notificações (PATCH e POST), seja qual for o perfil
                        .requestMatchers("/api/notificacoes/**").authenticated()
                        // Antes das regras de escrita: ALUNO e RESPONSAVEL só consultam o próprio aluno
                        .requestMatchers(HttpMethod.GET, "/api/**").access(new LeituraPorPerfilAuthorizationManager())
                        .requestMatchers("/api/avaliacoes/**", "/api/notas/**", "/api/frequencias/**")
                                .hasAnyRole(ADMIN, PROFESSOR)
                        .requestMatchers("/api/**").hasRole(ADMIN)
                        .anyRequest().denyAll())
                .exceptionHandling(erros -> erros.accessDeniedHandler(acessoNegado))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .accessDeniedHandler(acessoNegado)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(perfilComoRole())));
        return http.build();
    }

    /**
     * 403 em ProblemDetail. Como bean, o Spring Security também o usa para os caminhos recusados
     * pelo firewall (RequestRejectedHandler).
     */
    @Bean
    AcessoNegadoHandler acessoNegadoHandler(JsonMapper json) {
        return new AcessoNegadoHandler(json);
    }

    /**
     * O Tomcat recusaria {@code %2F} no caminho com um 400 próprio, antes do Spring Security. Com
     * {@code passthrough}, o {@code %2F} chega intacto ao firewall do Spring Security, que recusa a
     * requisição (403 em ProblemDetail, como os outros caminhos suspeitos). Nada com {@code %2F}
     * chega às rotas.
     */
    @Bean
    TomcatConnectorCustomizer barraCodificadaParaOFirewall() {
        return connector -> connector.setEncodedSolidusHandling(EncodedSolidusHandling.PASS_THROUGH.getValue());
    }

    /** Mesmo segredo e algoritmo (HS256) usados pelo usuarios-service para assinar o token. */
    @Bean
    JwtDecoder jwtDecoder(@Value("${jwt.segredo}") String segredo) {
        var chave = new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** {@code "perfil": "ADMIN"} no token vira a role ROLE_ADMIN usada em hasRole("ADMIN"). */
    private static JwtAuthenticationConverter perfilComoRole() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("perfil");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewayRoutesProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(properties.allowedOrigin()));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
