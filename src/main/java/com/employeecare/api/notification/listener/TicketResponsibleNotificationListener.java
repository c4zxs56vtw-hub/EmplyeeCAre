package com.employeecare.api.notification.listener;

import com.employeecare.api.notification.email.TicketNotificationEmailService;
import com.employeecare.api.notification.event.TicketResponsibleNotificationEvent;
import com.employeecare.api.notification.service.TicketResponsibleRecipientService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class TicketResponsibleNotificationListener {

    private static final Logger log =
            LoggerFactory.getLogger(
                    TicketResponsibleNotificationListener.class
            );

    private final TicketResponsibleRecipientService recipientService;

    private final TicketNotificationEmailService emailService;

    public TicketResponsibleNotificationListener(
            TicketResponsibleRecipientService recipientService,
            TicketNotificationEmailService emailService
    ) {

        this.recipientService = recipientService;

        this.emailService = emailService;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(
            TicketResponsibleNotificationEvent event
    ) {

        try {

            // 1. Rechercher les destinataires autorisés.

            List<String> recipients =
                    recipientService.findRecipients(event);

            // 2. Préparer le message.

            String subject = getSubject(event.type());

            String message = getMessage(event.type());

            // 3. Envoyer les notifications.

            for (String email : recipients) {

                try {

                    emailService.sendNotification(
                            email,
                            subject,
                            message
                    );

                    log.info(
                            "Notification responsable envoyée : {}",
                            event.type()
                    );

                } catch (RuntimeException exception) {

                    log.error(
                            "Échec d'envoi d'une notification responsable : {}",
                            event.type(),
                            exception
                    );
                }
            }

        } catch (RuntimeException exception) {

            log.error(
                    "Échec du traitement des notifications responsables.",
                    exception
            );
        }
    }

    private String getSubject(
            TicketResponsibleNotificationEvent.Type type
    ) {

        return switch (type) {

            case NOUVEAU_SIGNALEMENT ->
                    "EmployeeCare - Nouveau signalement disponible";

            case SOLUTION_REFUSEE ->
                    "EmployeeCare - Une solution nécessite une nouvelle intervention";
        };
    }

    private String getMessage(
            TicketResponsibleNotificationEvent.Type type
    ) {

        return switch (type) {

            case NOUVEAU_SIGNALEMENT -> """

                    Bonjour,

                    Un nouveau signalement relevant de
                    votre périmètre de responsabilité
                    a été créé dans EmployeeCare.

                    Connectez-vous à votre espace
                    responsable pour le consulter.

                    Cordialement,
                    L'équipe EmployeeCare
                    """;

            case SOLUTION_REFUSEE -> """

                    Bonjour,

                    Une solution proposée pour un
                    signalement dont vous êtes responsable
                    a été refusée.

                    Une nouvelle intervention peut
                    être nécessaire.

                    Connectez-vous à EmployeeCare pour
                    consulter les observations de l'employé.

                    Cordialement,
                    L'équipe EmployeeCare
                    """;
        };
    }
}
