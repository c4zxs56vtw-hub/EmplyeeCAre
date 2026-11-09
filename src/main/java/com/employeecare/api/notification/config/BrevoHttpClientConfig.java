package com.employeecare.api.notification.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class BrevoHttpClientConfig {

    @Bean
    @ConditionalOnMissingBean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean("brevoRestClient")
    public RestClient brevoRestClient(
            RestClient.Builder builder
    ) {

        // 1. Configurer le client HTTP Java
        HttpClient httpClient = HttpClient
                .newBuilder()
                .connectTimeout(
                        Duration.ofSeconds(3)
                )
                .build();

        // 2. Adapter ce client à Spring
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(
                        httpClient
                );

        // 3. Configurer le délai de réponse
        requestFactory.setReadTimeout(
                Duration.ofSeconds(5)
        );

        // 4. Construire le client Brevo
        return builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(
                        "https://api.brevo.com/v3"
                )
                .build();
    }
}
