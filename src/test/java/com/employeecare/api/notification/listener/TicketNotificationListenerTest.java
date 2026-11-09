package com.employeecare.api.notification.listener;

import com.employeecare.api.notification.email.TicketNotificationEmailService;
import com.employeecare.api.notification.event.TicketEmailNotificationEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketNotificationListenerTest {

    @Mock
    private TicketNotificationEmailService emailService;

    @InjectMocks
    private TicketNotificationListener listener;

    @Test
    @DisplayName("handleNotification - Envoie l'email de prise en charge")
    void handleNotification_priseEnCharge_shouldSendEmail() {
        TicketEmailNotificationEvent event = new TicketEmailNotificationEvent(
                "claire@entreprise.com",
                TicketEmailNotificationEvent.Type.PRISE_EN_CHARGE
        );

        listener.handleNotification(event);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendNotification(eq("claire@entreprise.com"), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("Votre signalement a été pris en charge par un responsable.");
    }

    @Test
    @DisplayName("handleNotification - Envoie l'email de solution proposée")
    void handleNotification_solutionProposee_shouldSendEmail() {
        TicketEmailNotificationEvent event = new TicketEmailNotificationEvent(
                "claire@entreprise.com",
                TicketEmailNotificationEvent.Type.SOLUTION_PROPOSEE
        );

        listener.handleNotification(event);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendNotification(eq("claire@entreprise.com"), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("Une solution a été proposée pour l'un de vos signalements.");
    }

    @Test
    @DisplayName("handleNotification - Ne propage pas l'exception si Brevo échoue")
    void handleNotification_whenBrevoFails_shouldNotThrow() {
        TicketEmailNotificationEvent event = new TicketEmailNotificationEvent(
                "claire@entreprise.com",
                TicketEmailNotificationEvent.Type.PRISE_EN_CHARGE
        );

        doThrow(new RuntimeException("Brevo indisponible"))
                .when(emailService).sendNotification(anyString(), anyString());

        assertThatCode(() -> listener.handleNotification(event))
                .doesNotThrowAnyException();
    }
}
