package com.distrischool.api_gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public record GatewayRoutesProperties(
        String administrativaUrl,
        String escolarUrl,
        String allowedOrigin
) {
}
