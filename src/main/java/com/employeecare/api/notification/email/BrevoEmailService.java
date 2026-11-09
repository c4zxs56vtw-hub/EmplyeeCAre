package com.employeecare.api.notification.email;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class BrevoEmailService implements EmailService {

    private final RestClient restClient;

    private final String apiKey;

    private final String senderEmail;

    private final String senderName;

    public BrevoEmailService(
            @Qualifier("brevoRestClient") RestClient restClient,
            @Value("${brevo.api.key}") String apiKey,
            @Value("${brevo.sender.email}") String senderEmail,
            @Value("${brevo.sender.name}") String senderName
    ) {

        this.restClient = restClient;
        this.apiKey = apiKey;
        this.senderEmail = senderEmail;
        this.senderName = senderName;
    }

    @Override
    public void sendActivationEmail(
            String recipientEmail,
            String recipientName,
            String activationLink
    ) {

        String message = """
                Bonjour %s,

                Bienvenue sur EmployeeCare !

                Un compte a été créé pour vous.

                Pour activer votre compte et définir
                votre mot de passe, cliquez sur le lien :

                %s

                Si vous n'êtes pas à l'origine de cette
                demande, contactez votre administrateur.

                Cordialement,
                L'équipe EmployeeCare
                """.formatted(
                recipientName,
                activationLink
        );

        Map<String, Object> emailRequest = Map.of(

                "sender", Map.of(
                        "name", senderName,
                        "email", senderEmail
                ),

                "to", List.of(
                        Map.of(
                                "email", recipientEmail,
                                "name", recipientName
                        )
                ),

                "subject", "Activation de votre compte EmployeeCare",

                "textContent", message
        );

        restClient.post()
                .uri("/smtp/email")
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(emailRequest)
                .retrieve()
                .toBodilessEntity();
    }
}
