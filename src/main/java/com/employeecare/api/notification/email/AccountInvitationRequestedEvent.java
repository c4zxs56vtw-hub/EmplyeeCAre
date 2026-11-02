package com.employeecare.api.notification.email;

public record AccountInvitationRequestedEvent(

        String recipientEmail,

        String recipientName,

        String token

) {
}