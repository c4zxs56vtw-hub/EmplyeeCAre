package com.employeecare.api.notification.event;

public record TicketEmailNotificationEvent(
        String recipientEmail,
        Type type
) {

    public enum Type {
        PRISE_EN_CHARGE,
        SOLUTION_PROPOSEE
    }
}
