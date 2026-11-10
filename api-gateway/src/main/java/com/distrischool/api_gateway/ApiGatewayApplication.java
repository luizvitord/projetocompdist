package com.distrischool.api_gateway;

import com.distrischool.api_gateway.config.GatewayRoutesProperties;
import com.distrischool.api_gateway.resiliencia.ResilienciaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({GatewayRoutesProperties.class, ResilienciaProperties.class})
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
