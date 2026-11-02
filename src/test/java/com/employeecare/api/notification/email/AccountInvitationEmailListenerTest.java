package com.employeecare.api.notification.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountInvitationEmailListenerTest {

    private static final String ACTIVATION_URL = "http://localhost:3000/activate";

    @Mock
    private EmailService emailService;

    private AccountInvitationEmailListener listener;

    @BeforeEach
    void setUp() {
        listener = new AccountInvitationEmailListener(emailService, ACTIVATION_URL);
    }

    @Test
    void handleInvitation_construitLeLienEtEnvoieLEmail() {

        listener.handleInvitation(new AccountInvitationRequestedEvent(
                "claire.moreau@entreprise.com",
                "Claire",
                "_zm6v1KljPLxNNsqswgBr4uSq4NApGPjHo55k6uShbE"
        ));

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);

        verify(emailService).sendActivationEmail(
                eq("claire.moreau@entreprise.com"),
                eq("Claire"),
                linkCaptor.capture()
        );

        assertThat(linkCaptor.getValue()).isEqualTo(
                ACTIVATION_URL + "?token=_zm6v1KljPLxNNsqswgBr4uSq4NApGPjHo55k6uShbE"
        );
    }

    @Test
    void handleInvitation_echecBrevo_nePropagePasLException() {

        doThrow(new RuntimeException("Unauthorized"))
                .when(emailService)
                .sendActivationEmail(anyString(), anyString(), anyString());

        // L'échec d'envoi ne doit pas faire échouer la création du compte
        assertThatCode(() -> listener.handleInvitation(new AccountInvitationRequestedEvent(
                "claire.moreau@entreprise.com",
                "Claire",
                "token"
        ))).doesNotThrowAnyException();
    }
}
