package com.distrischool.api_gateway.resiliencia;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryGetFilterTest {

    private final RetryGetFilter filter = new RetryGetFilter(new ResilienciaProperties.Retry(2, Duration.ofMillis(1)));
    private final AtomicInteger calls = new AtomicInteger();

    private static ServerRequest request(String method) {
        List<HttpMessageConverter<?>> converters = List.of();
        return ServerRequest.create(new MockHttpServletRequest(method, "/api/alunos"), converters);
    }

    private HandlerFunction<ServerResponse> answering(HttpStatus status) {
        return request -> {
            calls.incrementAndGet();
            return ServerResponse.status(status).build();
        };
    }

    private HandlerFunction<ServerResponse> failingWith(Exception cause) {
        return request -> {
            calls.incrementAndGet();
            throw new ResourceAccessException("I/O error", new java.io.IOException(cause));
        };
    }

    @Test
    void retriesGetOnGatewayErrorsUpToTheLimit() throws Exception {
        for (HttpStatus status : List.of(HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT)) {
            calls.set(0);

            ServerResponse response = filter.filter(request("GET"), answering(status));

            assertThat(response.statusCode()).isEqualTo(status);
            assertThat(calls).hasValue(3);
        }
    }

    @Test
    void doesNotRetryInternalServerErrorOrSuccess() throws Exception {
        filter.filter(request("GET"), answering(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThat(calls).hasValue(1);

        calls.set(0);
        filter.filter(request("GET"), answering(HttpStatus.OK));
        assertThat(calls).hasValue(1);
    }

    @Test
    void retriesGetOnConnectionErrors() {
        for (Exception cause : List.of(new ConnectException("Connection refused"),
                new HttpConnectTimeoutException("HTTP connect timed out"), new UnknownHostException("escolar"))) {
            calls.set(0);

            assertThatThrownBy(() -> filter.filter(request("GET"), failingWith(cause)))
                    .isInstanceOf(ResourceAccessException.class);
            assertThat(calls).hasValue(3);
        }
    }

    @Test
    void doesNotRetryReadTimeoutBecauseTheServiceMayHaveProcessedTheRequest() {
        assertThatThrownBy(() -> filter.filter(request("GET"), failingWith(new HttpTimeoutException("request timed out"))))
                .isInstanceOf(ResourceAccessException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void neverResendsNonIdempotentMethods() {
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            calls.set(0);

            assertThatThrownBy(() -> filter.filter(request(method), failingWith(new ConnectException("Connection refused"))))
                    .isInstanceOf(ResourceAccessException.class);
            assertThat(calls).as(method).hasValue(1);
        }
    }

    @Test
    void returnsTheRecoveredResponse() throws Exception {
        HandlerFunction<ServerResponse> flaky = request -> calls.incrementAndGet() == 1
                ? ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).build()
                : ServerResponse.ok().build();

        ServerResponse response = filter.filter(request("GET"), flaky);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
        assertThat(calls).hasValue(2);
    }
}
