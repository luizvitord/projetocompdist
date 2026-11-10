package com.distrischool.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
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

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * O gateway é a porta de entrada: toda requisição para /api precisa de um JWT válido, emitido
 * pelo usuarios-service no login. Os serviços de trás confiam no gateway e não validam o token.
 * <p>
 * Regras por perfil (claim {@code perfil} do token), na ordem em que são avaliadas:
 * <ul>
 *   <li>login, health checks e Swagger: livres;</li>
 *   <li>administração de usuários: só ADMIN;</li>
 *   <li>consultas (GET): qualquer usuário autenticado;</li>
 *   <li>lançar notas e frequência: ADMIN ou PROFESSOR;</li>
 *   <li>demais alterações (cadastros, turmas, matrículas): só ADMIN.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String PROFESSOR = "PROFESSOR";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API sem sessão nem cookies: o token vai em cada requisição, então não há CSRF
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        // Swagger de cada serviço
                        .requestMatchers("/administrativa/**", "/escolar/**", "/usuarios/**").permitAll()

                        .requestMatchers("/api/usuarios/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                        .requestMatchers("/api/avaliacoes/**", "/api/notas/**", "/api/frequencias/**")
                                .hasAnyRole(ADMIN, PROFESSOR)
                        .requestMatchers("/api/**").hasRole(ADMIN)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(perfilComoRole())));
        return http.build();
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
