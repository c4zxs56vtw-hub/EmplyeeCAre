package com.employeecare.api.notification.listener;

import com.employeecare.api.notification.email.TicketNotificationEmailService;
import com.employeecare.api.notification.event.TicketResponsibleNotificationEvent;
import com.employeecare.api.notification.service.TicketResponsibleRecipientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketResponsibleNotificationListenerTest {

    @Mock
    private TicketResponsibleRecipientService recipientService;

    @Mock
    private TicketNotificationEmailService emailService;

    @InjectMocks
    private TicketResponsibleNotificationListener listener;

    @Test
    @DisplayName("handle - NOUVEAU_SIGNALEMENT envoie un email à chaque responsable habilité")
    void handle_nouveauSignalement_shouldSendToAllRecipients() {
        UUID ticketId = UUID.randomUUID();
        TicketResponsibleNotificationEvent event = new TicketResponsibleNotificationEvent(
                ticketId,
                TicketResponsibleNotificationEvent.Type.NOUVEAU_SIGNALEMENT
        );

        when(recipientService.findRecipients(event))
                .thenReturn(List.of("paul@entreprise.com", "sophie@entreprise.com"));

        listener.handle(event);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendNotification(
                eq("paul@entreprise.com"),
                eq("EmployeeCare - Nouveau signalement disponible"),
                messageCaptor.capture()
        );
        assertThat(messageCaptor.getValue()).contains("Un nouveau signalement relevant de");
        verify(emailService).sendNotification(
                eq("sophie@entreprise.com"),
                eq("EmployeeCare - Nouveau signalement disponible"),
                eq(messageCaptor.getValue())
        );
    }

    @Test
    @DisplayName("handle - SOLUTION_REFUSEE envoie l'email au responsable attribué")
    void handle_solutionRefusee_shouldSendToAssignedResponsable() {
        UUID ticketId = UUID.randomUUID();
        TicketResponsibleNotificationEvent event = new TicketResponsibleNotificationEvent(
                ticketId,
                TicketResponsibleNotificationEvent.Type.SOLUTION_REFUSEE
        );

        when(recipientService.findRecipients(event))
                .thenReturn(List.of("sophie@entreprise.com"));

        listener.handle(event);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendNotification(
                eq("sophie@entreprise.com"),
                eq("EmployeeCare - Une solution nécessite une nouvelle intervention"),
                messageCaptor.capture()
        );
        assertThat(messageCaptor.getValue()).contains("Une solution proposée pour un");
    }

    @Test
    @DisplayName("handle - L'échec sur un destinataire n'empêche pas l'envoi aux autres")
    void handle_whenFirstRecipientFails_shouldStillSendToSecondRecipient() {
        UUID ticketId = UUID.randomUUID();
        TicketResponsibleNotificationEvent event = new TicketResponsibleNotificationEvent(
                ticketId,
                TicketResponsibleNotificationEvent.Type.NOUVEAU_SIGNALEMENT
        );

        when(recipientService.findRecipients(event))
                .thenReturn(List.of("paul@entreprise.com", "sophie@entreprise.com"));

        doThrow(new RuntimeException("Timeout Brevo"))
                .when(emailService)
                .sendNotification(eq("paul@entreprise.com"), anyString(), anyString());

        listener.handle(event);

        verify(emailService).sendNotification(
                eq("sophie@entreprise.com"),
                eq("EmployeeCare - Nouveau signalement disponible"),
                anyString()
        );
    }
}
