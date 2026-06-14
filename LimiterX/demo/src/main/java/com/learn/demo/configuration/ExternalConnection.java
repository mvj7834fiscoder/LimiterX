package com.learn.demo.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ExternalConnection {

    @Bean
    public WebClient externalCallConnection() {
        return WebClient.builder()
                .baseUrl("http://localhost:8090")
                .build();
    }
}
