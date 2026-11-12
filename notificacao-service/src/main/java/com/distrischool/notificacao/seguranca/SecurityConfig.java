package com.distrischool.notificacao.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Diferente dos outros serviços de negócio, este valida o JWT (resource server, mesmo segredo
 * HS256 do usuarios-service e do api-gateway): as notificações são de quem está logado, e o
 * destinatário sai das claims do token, não de parâmetros.
 * <p>
 * Health checks, métricas do Prometheus e Swagger ficam livres, como nos outros serviços.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API sem sessão nem cookies: o token vai em cada requisição, então não há CSRF
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        // Swagger
                        .requestMatchers("/notificacao/**").permitAll()
                        // Handshake do WebSocket: o navegador não envia cabeçalhos nele, então o
                        // token é validado no frame CONNECT do STOMP (StompAutenticacaoInterceptor)
                        .requestMatchers("/ws").permitAll()
                        .requestMatchers("/api/notificacoes/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** Mesmo segredo e algoritmo (HS256) usados pelo usuarios-service para assinar o token. */
    @Bean
    JwtDecoder jwtDecoder(@Value("${jwt.segredo}") String segredo) {
        var chave = new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
        // Assinatura e expiração (padrão) + perfil conhecido
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), DestinatarioJwt.perfilValido()));
        return decoder;
    }
}
