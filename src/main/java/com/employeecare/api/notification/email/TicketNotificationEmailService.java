package com.employeecare.api.notification.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class TicketNotificationEmailService {

    private final RestClient restClient;
    private final String senderEmail;
    private final String senderName;

    public TicketNotificationEmailService(
            RestClient.Builder builder,
            @Value("${brevo.api.key}") String apiKey,
            @Value("${brevo.sender.email}") String senderEmail,
            @Value("${brevo.sender.name:EmployeeCare}") String senderName
    ) {
        this.restClient = builder
                .baseUrl("https://api.brevo.com/v3")
                .defaultHeader("api-key", apiKey)
                .build();
        this.senderEmail = senderEmail;
        this.senderName = senderName;
    }

    public void sendNotification(
            String recipientEmail,
            String message
    ) {
        String subject = "EmployeeCare - Mise à jour de votre signalement";

        Map<String, Object> payload = Map.of(
                "sender", Map.of(
                        "name", senderName,
                        "email", senderEmail
                ),
                "to", List.of(
                        Map.of("email", recipientEmail)
                ),
                "subject", subject,
                "textContent", message
        );

        restClient.post()
                .uri("/smtp/email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
