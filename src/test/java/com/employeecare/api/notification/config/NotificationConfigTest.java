package com.employeecare.api.notification.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationConfigTest {

    @Test
    @DisplayName("notificationExecutor - Configure correctement le pool de threads asynchrone")
    void notificationExecutor_shouldConfigureThreadPool() {
        NotificationAsyncConfig config = new NotificationAsyncConfig();

        Executor executor = config.notificationExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(4);
        assertThat(taskExecutor.getQueueCapacity()).isEqualTo(100);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("employeecare-mail-");
        taskExecutor.shutdown();
    }

    @Test
    @DisplayName("brevoRestClient - Construit un RestClient configuré avec les délais d'attente")
    void brevoRestClient_shouldCreateConfiguredRestClient() {
        BrevoHttpClientConfig config = new BrevoHttpClientConfig();

        RestClient.Builder builder = config.restClientBuilder();
        RestClient client = config.brevoRestClient(builder);

        assertThat(client).isNotNull();
    }
}
