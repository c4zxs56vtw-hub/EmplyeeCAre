package com.employeecare.api.notification.event;

import java.util.UUID;

public record TicketResponsibleNotificationEvent(

        UUID ticketId,

        Type type

) {

    public enum Type {

        NOUVEAU_SIGNALEMENT,

        SOLUTION_REFUSEE
    }
}
