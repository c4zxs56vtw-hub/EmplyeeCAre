package com.employeecare.api.ticket.service;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.generated.models.CreateTicketRequest;
import com.employeecare.api.generated.models.TicketSummary;
import com.employeecare.api.ticket.entity.Ticket;
import com.employeecare.api.ticket.entity.TicketCategory;
import com.employeecare.api.ticket.repository.TicketCategoryRepository;
import com.employeecare.api.ticket.repository.TicketRepository;
import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;
import com.employeecare.api.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketCategoryRepository categoryRepository;

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private TicketService ticketService;

    private UUID employeeId;
    private AppUser employee;
    private Department department;
    private TicketCategory category;

    @BeforeEach
    void setUp() {
        employeeId = UUID.randomUUID();

        department = new Department();
        department.setId(UUID.randomUUID());
        department.setName("Administration");
        department.setActive(true);

        Role employeRole = new Role();
        employeRole.setCode("EMPLOYE");

        employee = new AppUser();
        employee.setId(employeeId);
        employee.setStatus(AccountStatus.ACTIF);
        employee.setDepartment(department);
        employee.setRoles(new HashSet<>(Set.of(employeRole)));

        category = new TicketCategory();
        category.setCode("TECHNIQUE");
        category.setLabel("Problèmes techniques");
        category.setActive(true);
    }

    @Test
    @DisplayName("createTicket - Succès avec génération de référence")
    void createTicket_shouldSucceed() {
        CreateTicketRequest request = new CreateTicketRequest()
                .title("Impossible d'accéder au réseau")
                .description("Depuis ce matin, je ne parviens plus à accéder au réseau informatique.")
                .categoryCode("technique")
                .isConfidential(false);

        when(userRepository.findByIdWithRoles(employeeId)).thenReturn(Optional.of(employee));
        when(categoryRepository.findById("TECHNIQUE")).thenReturn(Optional.of(category));
        when(jdbcTemplate.queryForObject(eq("SELECT nextval('ticket_reference_seq')"), eq(Long.class)))
                .thenReturn(1L);

        UUID ticketId = UUID.randomUUID();
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.setId(ticketId);
            t.setCreatedAt(Instant.parse("2026-11-04T10:00:00Z"));
            return t;
        });

        TicketSummary summary = ticketService.createTicket(employeeId, request);

        assertThat(summary.getId()).isEqualTo(ticketId.toString());
        assertThat(summary.getReference()).matches("SIG-\\d{4}-000001");
        assertThat(summary.getTitle()).isEqualTo("Impossible d'accéder au réseau");
        assertThat(summary.getCategory()).isEqualTo("Problèmes techniques");
        assertThat(summary.getPriority()).isEqualTo(TicketSummary.PriorityEnum.MOYENNE);
        assertThat(summary.getStatus()).isEqualTo(TicketSummary.StatusEnum.EN_ATTENTE);
        assertThat(summary.getIsConfidential()).isFalse();
        assertThat(summary.getCreatedAt()).isEqualTo("04/11/2026");
    }

    @Test
    @DisplayName("createTicket - Utilisateur introuvable lève 401")
    void createTicket_userNotFound_shouldThrow401() {
        when(userRepository.findByIdWithRoles(employeeId)).thenReturn(Optional.empty());

        CreateTicketRequest request = new CreateTicketRequest()
                .title("Titre valide")
                .description("Description suffisamment longue")
                .categoryCode("TECHNIQUE");

        assertThatThrownBy(() -> ticketService.createTicket(employeeId, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("createTicket - Catégorie inexistante ou désactivée lève 400")
    void createTicket_invalidCategory_shouldThrow400() {
        when(userRepository.findByIdWithRoles(employeeId)).thenReturn(Optional.of(employee));
        when(categoryRepository.findById("INCONNUE")).thenReturn(Optional.empty());

        CreateTicketRequest request = new CreateTicketRequest()
                .title("Titre valide")
                .description("Description suffisamment longue")
                .categoryCode("INCONNUE");

        assertThatThrownBy(() -> ticketService.createTicket(employeeId, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
