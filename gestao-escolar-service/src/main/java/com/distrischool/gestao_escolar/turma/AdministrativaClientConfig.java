package com.distrischool.gestao_escolar.turma;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AdministrativaClientConfig {

    /** Mesmo timeout para conectar e para esperar a resposta. */
    @Bean
    RestClient administrativaRestClient(RestClient.Builder builder,
                                        @Value("${escolar.administrativa.url}") String url,
                                        @Value("${escolar.administrativa.timeout}") Duration timeout) {
        return criarRestClient(builder, url, timeout);
    }

    static RestClient criarRestClient(RestClient.Builder builder, String url, Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return builder.baseUrl(url).requestFactory(requestFactory).build();
    }
}
