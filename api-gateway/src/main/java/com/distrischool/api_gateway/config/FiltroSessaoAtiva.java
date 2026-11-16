package com.distrischool.api_gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Recusa, após a assinatura do JWT, uma sessão que foi revogada no usuários-service. */
public class FiltroSessaoAtiva extends OncePerRequestFilter {

    private final ValidadorSessaoUsuario validadorSessaoUsuario;

    public FiltroSessaoAtiva(ValidadorSessaoUsuario validadorSessaoUsuario) {
        this.validadorSessaoUsuario = validadorSessaoUsuario;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao instanceof JwtAuthenticationToken token) {
            ValidadorSessaoUsuario.Resultado resultado = validadorSessaoUsuario.validar(token.getToken());
            if (resultado != ValidadorSessaoUsuario.Resultado.VALIDA) {
                SecurityContextHolder.clearContext();
                if (resultado == ValidadorSessaoUsuario.Resultado.REVOGADA) {
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                } else {
                    response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                }
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
