package com.employeecare.api.notification.service;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.notification.event.TicketResponsibleNotificationEvent;
import com.employeecare.api.responsable.entity.ResponsableAccount;
import com.employeecare.api.responsable.repository.ResponsableAccountRepository;
import com.employeecare.api.ticket.entity.Ticket;
import com.employeecare.api.ticket.entity.TicketCategory;
import com.employeecare.api.ticket.repository.TicketRepository;
import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketResponsibleRecipientServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ResponsableAccountRepository responsableRepository;

    @InjectMocks
    private TicketResponsibleRecipientService recipientService;

    @Test
    @DisplayName("findRecipients - NOUVEAU_SIGNALEMENT retourne tous les responsables éligibles")
    void findRecipients_nouveauSignalement_shouldReturnAllEligibleEmails() {
        UUID ticketId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        Department department = new Department();
        department.setId(departmentId);

        TicketCategory category = new TicketCategory();
        category.setCode("TECHNIQUE");

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setConfidential(false);

        AppUser paul = new AppUser();
        paul.setId(UUID.randomUUID());
        paul.setEmail("paul@entreprise.com");

        ResponsableAccount paulAccount = new ResponsableAccount();
        paulAccount.setUserId(paul.getId());
        paulAccount.setUser(paul);

        AppUser sophie = new AppUser();
        sophie.setId(UUID.randomUUID());
        sophie.setEmail("sophie@entreprise.com");

        ResponsableAccount sophieAccount = new ResponsableAccount();
        sophieAccount.setUserId(sophie.getId());
        sophieAccount.setUser(sophie);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(responsableRepository.findEligibleForTicket(
                departmentId, "TECHNIQUE", false, AccountStatus.ACTIF
        )).thenReturn(List.of(paulAccount, sophieAccount));

        List<String> recipients = recipientService.findRecipients(
                new TicketResponsibleNotificationEvent(
                        ticketId,
                        TicketResponsibleNotificationEvent.Type.NOUVEAU_SIGNALEMENT
                )
        );

        assertThat(recipients).containsExactly("paul@entreprise.com", "sophie@entreprise.com");
    }

    @Test
    @DisplayName("findRecipients - SOLUTION_REFUSEE retourne uniquement le responsable attribué")
    void findRecipients_solutionRefusee_shouldReturnOnlyAssignedResponsable() {
        UUID ticketId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        Department department = new Department();
        department.setId(departmentId);

        TicketCategory category = new TicketCategory();
        category.setCode("TECHNIQUE");

        AppUser paul = new AppUser();
        paul.setId(UUID.randomUUID());
        paul.setEmail("paul@entreprise.com");

        ResponsableAccount paulAccount = new ResponsableAccount();
        paulAccount.setUserId(paul.getId());
        paulAccount.setUser(paul);

        AppUser sophie = new AppUser();
        sophie.setId(UUID.randomUUID());
        sophie.setEmail("sophie@entreprise.com");

        ResponsableAccount sophieAccount = new ResponsableAccount();
        sophieAccount.setUserId(sophie.getId());
        sophieAccount.setUser(sophie);

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setConfidential(false);
        ticket.setAssignedResponsable(sophie);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(responsableRepository.findEligibleForTicket(
                departmentId, "TECHNIQUE", false, AccountStatus.ACTIF
        )).thenReturn(List.of(paulAccount, sophieAccount));

        List<String> recipients = recipientService.findRecipients(
                new TicketResponsibleNotificationEvent(
                        ticketId,
                        TicketResponsibleNotificationEvent.Type.SOLUTION_REFUSEE
                )
        );

        assertThat(recipients).containsExactly("sophie@entreprise.com");
    }
}
