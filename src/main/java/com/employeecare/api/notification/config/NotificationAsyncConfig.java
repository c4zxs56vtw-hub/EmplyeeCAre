package com.employeecare.api.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class NotificationAsyncConfig {

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {

        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        // Nombre de threads permanents
        executor.setCorePoolSize(2);

        // Nombre maximal de threads
        executor.setMaxPoolSize(4);

        // Nombre maximal de tâches en attente
        executor.setQueueCapacity(100);

        // Nom des threads dans les logs
        executor.setThreadNamePrefix(
                "employeecare-mail-"
        );

        // En cas de saturation, le thread appelant
        // exécute la tâche au lieu de la supprimer.
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // Attendre la fin des tâches lors de l'arrêt
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);

        executor.initialize();

        return executor;
    }
}
