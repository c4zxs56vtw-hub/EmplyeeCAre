package com.employeecare.api.notification.service;

import com.employeecare.api.notification.event.TicketResponsibleNotificationEvent;
import com.employeecare.api.responsable.entity.ResponsableAccount;
import com.employeecare.api.responsable.repository.ResponsableAccountRepository;
import com.employeecare.api.ticket.entity.Ticket;
import com.employeecare.api.ticket.repository.TicketRepository;
import com.employeecare.api.user.entity.AccountStatus;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TicketResponsibleRecipientService {

    private final TicketRepository ticketRepository;

    private final ResponsableAccountRepository responsableRepository;

    public TicketResponsibleRecipientService(
            TicketRepository ticketRepository,
            ResponsableAccountRepository responsableRepository
    ) {

        this.ticketRepository = ticketRepository;

        this.responsableRepository = responsableRepository;
    }

    @Transactional(
            readOnly = true,
            propagation = Propagation.REQUIRES_NEW
    )
    public List<String> findRecipients(
            TicketResponsibleNotificationEvent event
    ) {

        // 1. Rechercher le signalement.

        Ticket ticket = ticketRepository
                .findById(event.ticketId())
                .orElse(null);

        if (ticket == null) {
            return List.of();
        }

        // 2. Récupérer son département et sa catégorie.

        UUID departmentId = ticket.getDepartment().getId();

        String categoryCode = ticket.getCategory().getCode();

        // 3. Rechercher les responsables habilités.

        List<ResponsableAccount> eligible =
                responsableRepository.findEligibleForTicket(

                        departmentId,

                        categoryCode,

                        ticket.isConfidential(),

                        AccountStatus.ACTIF
                );

        // 4. Filtrer les destinataires selon l'événement.

        return eligible.stream()

                .filter(responsable -> {

                    if (event.type()
                            == TicketResponsibleNotificationEvent.Type.NOUVEAU_SIGNALEMENT) {

                        return true;
                    }

                    // Lorsqu'une solution est refusée,
                    // notifier uniquement le responsable attribué.

                    return ticket.getAssignedResponsable() != null

                            && responsable.getUserId().equals(
                                    ticket.getAssignedResponsable().getId()
                            );
                })

                .map(responsable ->
                        responsable.getUser().getEmail()
                )

                .filter(email ->
                        email != null && !email.isBlank()
                )

                .distinct()

                .toList();
    }
}
