package com.employeecare.api.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AccountInvitationEmailListener {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    AccountInvitationEmailListener.class
            );

    private final EmailService emailService;

    private final String activationUrl;

    public AccountInvitationEmailListener(
            EmailService emailService,

            @Value("${employeecare.frontend.activation-url}")
            String activationUrl
    ) {
        this.emailService = emailService;
        this.activationUrl = activationUrl;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleInvitation(
            AccountInvitationRequestedEvent event
    ) {

        String activationLink = UriComponentsBuilder
                .fromUriString(activationUrl)
                .queryParam("token", event.token())
                .build()
                .encode()
                .toUriString();

        try {

            emailService.sendActivationEmail(
                    event.recipientEmail(),
                    event.recipientName(),
                    activationLink
            );

            logger.info(
                    "Demande d'envoi de l'invitation transmise à Brevo."
            );

        } catch (RuntimeException exception) {

            logger.error(
                    "Echec de l'envoi de l'invitation : {}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}