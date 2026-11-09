package com.employeecare.api.notification.listener;

import com.employeecare.api.notification.email.TicketNotificationEmailService;
import com.employeecare.api.notification.event.TicketEmailNotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TicketNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(
            TicketNotificationListener.class
    );

    private final TicketNotificationEmailService emailService;

    public TicketNotificationListener(
            TicketNotificationEmailService emailService
    ) {
        this.emailService = emailService;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleNotification(
            TicketEmailNotificationEvent event
    ) {
        String message = switch (event.type()) {
            case PRISE_EN_CHARGE -> """
                    Bonjour,

                    Votre signalement a été pris en charge par un responsable.
                    Son traitement est maintenant en cours.

                    Connectez-vous à EmployeeCare pour consulter son évolution.

                    Cordialement,
                    L'équipe EmployeeCare
                    """;
            case SOLUTION_PROPOSEE -> """
                    Bonjour,

                    Une solution a été proposée pour l'un de vos signalements.
                    Votre validation est maintenant attendue.

                    Connectez-vous à EmployeeCare pour consulter la solution et donner votre avis.

                    Cordialement,
                    L'équipe EmployeeCare
                    """;
        };

        try {
            emailService.sendNotification(
                    event.recipientEmail(),
                    message
            );
            log.info(
                    "Notification EmployeeCare envoyée : {}",
                    event.type()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Échec d'envoi de la notification : {}",
                    event.type(),
                    exception
            );
        }
    }
}
