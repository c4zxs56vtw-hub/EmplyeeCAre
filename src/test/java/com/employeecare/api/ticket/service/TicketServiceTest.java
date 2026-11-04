package com.employeecare.api.ticket.service;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.generated.models.CreateTicketRequest;
import com.employeecare.api.generated.models.ReviewTicketRequest;
import com.employeecare.api.generated.models.TicketHistoryEvent;
import com.employeecare.api.generated.models.TicketSummary;
import com.employeecare.api.generated.models.UpdateTicketStatusRequest;
import com.employeecare.api.responsable.entity.ResponsableAccount;
import com.employeecare.api.responsable.repository.ResponsableAccountRepository;
import com.employeecare.api.ticket.entity.Ticket;
import com.employeecare.api.ticket.entity.TicketCategory;
import com.employeecare.api.ticket.entity.TicketEvent;
import com.employeecare.api.ticket.entity.TicketPriority;
import com.employeecare.api.ticket.entity.TicketStatus;
import com.employeecare.api.ticket.repository.TicketCategoryRepository;
import com.employeecare.api.ticket.repository.TicketEventRepository;
import com.employeecare.api.ticket.repository.TicketRepository;
import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;
import com.employeecare.api.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    private ResponsableAccountRepository responsableAccountRepository;

    @Mock
    private TicketEventRepository ticketEventRepository;

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

    @Test
    @DisplayName("getMyTickets - Retourne la liste des signalements de l'employé")
    void getMyTickets_shouldReturnMappedSummaries() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setReference("SIG-2026-000001");
        ticket.setTitle("Impossible d'accéder au réseau");
        ticket.setCategory(category);
        ticket.setPriority(TicketPriority.MOYENNE);
        ticket.setStatus(TicketStatus.EN_ATTENTE);
        ticket.setConfidential(true);
        ticket.setCreatedAt(Instant.parse("2026-11-04T10:00:00Z"));

        when(ticketRepository.findByEmployee_IdOrderByCreatedAtDesc(employeeId))
                .thenReturn(List.of(ticket));

        List<TicketSummary> result = ticketService.getMyTickets(employeeId);

        assertThat(result).hasSize(1);
        TicketSummary summary = result.get(0);
        assertThat(summary.getId()).isEqualTo(ticketId.toString());
        assertThat(summary.getReference()).isEqualTo("SIG-2026-000001");
        assertThat(summary.getTitle()).isEqualTo("Impossible d'accéder au réseau");
        assertThat(summary.getCategory()).isEqualTo("Problèmes techniques");
        assertThat(summary.getPriority()).isEqualTo(TicketSummary.PriorityEnum.MOYENNE);
        assertThat(summary.getStatus()).isEqualTo(TicketSummary.StatusEnum.EN_ATTENTE);
        assertThat(summary.getIsConfidential()).isTrue();
        assertThat(summary.getCreatedAt()).isEqualTo("04/11/2026");
    }

    @Test
    @DisplayName("getResponsableTickets - Retourne uniquement les signalements visibles du périmètre")
    void getResponsableTickets_shouldReturnVisibleTickets() {
        UUID responsableId = UUID.randomUUID();
        Set<String> categories = Set.of("TECHNIQUE", "RH");

        ResponsableAccount account = new ResponsableAccount();
        account.setUserId(responsableId);
        account.setActive(true);
        account.setDepartment(department);
        account.setAssignedCategories(new HashSet<>(categories));
        account.setConfidentialHabilitation(false);

        Ticket ticket = new Ticket();
        ticket.setId(UUID.randomUUID());
        ticket.setReference("SIG-2026-000002");
        ticket.setTitle("Problème réseau");
        ticket.setCategory(category);
        ticket.setPriority(TicketPriority.MOYENNE);
        ticket.setStatus(TicketStatus.EN_ATTENTE);
        ticket.setConfidential(false);
        ticket.setCreatedAt(Instant.parse("2026-11-04T10:00:00Z"));

        when(responsableAccountRepository.findById(responsableId)).thenReturn(Optional.of(account));
        when(ticketRepository.findVisibleForResponsable(department.getId(), account.getAssignedCategories(), false))
                .thenReturn(List.of(ticket));

        List<TicketSummary> result = ticketService.getResponsableTickets(responsableId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReference()).isEqualTo("SIG-2026-000002");
    }

    @Test
    @DisplayName("getResponsableTickets - Retourne liste vide si aucune catégorie attribuée")
    void getResponsableTickets_withoutCategories_shouldReturnEmptyList() {
        UUID responsableId = UUID.randomUUID();

        ResponsableAccount account = new ResponsableAccount();
        account.setUserId(responsableId);
        account.setActive(true);
        account.setDepartment(department);
        account.setAssignedCategories(new HashSet<>());

        when(responsableAccountRepository.findById(responsableId)).thenReturn(Optional.of(account));

        List<TicketSummary> result = ticketService.getResponsableTickets(responsableId);

        assertThat(result).isEmpty();
        verifyNoInteractions(ticketRepository);
    }

    @Test
    @DisplayName("getResponsableTickets - Lève 403 si profil responsable inactif ou absent")
    void getResponsableTickets_inactiveOrMissingProfile_shouldThrow403() {
        UUID responsableId = UUID.randomUUID();

        when(responsableAccountRepository.findById(responsableId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getResponsableTickets(responsableId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponsableAccount inactive = new ResponsableAccount();
        inactive.setActive(false);
        when(responsableAccountRepository.findById(responsableId)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> ticketService.getResponsableTickets(responsableId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("takeCharge - Succès : attribue le responsable, passe en DIAGNOSTIC_EN_COURS et crée un TicketEvent")
    void takeCharge_shouldAssignTicketAndRecordEvent() {
        UUID responsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Role responsableRole = new Role();
        responsableRole.setCode("RESPONSABLE");

        AppUser actor = new AppUser();
        actor.setId(responsableId);
        actor.setStatus(AccountStatus.ACTIF);
        actor.setRoles(new HashSet<>(Set.of(responsableRole)));

        ResponsableAccount account = new ResponsableAccount();
        account.setUserId(responsableId);
        account.setUser(actor);
        account.setActive(true);
        account.setDepartment(department);
        account.setAssignedCategories(new HashSet<>(Set.of("TECHNIQUE")));
        account.setConfidentialHabilitation(false);

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setReference("SIG-2026-000001");
        ticket.setTitle("Problème réseau");
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setPriority(TicketPriority.MOYENNE);
        ticket.setStatus(TicketStatus.EN_ATTENTE);
        ticket.setConfidential(false);
        ticket.setCreatedAt(Instant.parse("2026-11-04T10:00:00Z"));

        when(responsableAccountRepository.findById(responsableId)).thenReturn(Optional.of(account));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);

        TicketSummary summary = ticketService.takeCharge(responsableId, ticketId);

        assertThat(summary.getStatus()).isEqualTo(TicketSummary.StatusEnum.DIAGNOSTIC_EN_COURS);
        assertThat(ticket.getAssignedResponsable()).isSameAs(actor);
        assertThat(ticket.getTakenAt()).isNotNull();

        ArgumentCaptor<TicketEvent> eventCaptor = ArgumentCaptor.forClass(TicketEvent.class);
        verify(ticketEventRepository).save(eventCaptor.capture());
        TicketEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getTicket()).isSameAs(ticket);
        assertThat(savedEvent.getActor()).isSameAs(actor);
        assertThat(savedEvent.getEventType()).isEqualTo("PRISE_EN_CHARGE");
        assertThat(savedEvent.getOldStatus()).isEqualTo(TicketStatus.EN_ATTENTE);
        assertThat(savedEvent.getNewStatus()).isEqualTo(TicketStatus.DIAGNOSTIC_EN_COURS);
    }

    @Test
    @DisplayName("takeCharge - Signalement déjà pris en charge lève 409 Conflict")
    void takeCharge_alreadyAssigned_shouldThrow409() {
        UUID responsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Role responsableRole = new Role();
        responsableRole.setCode("RESPONSABLE");

        AppUser actor = new AppUser();
        actor.setId(responsableId);
        actor.setStatus(AccountStatus.ACTIF);
        actor.setRoles(new HashSet<>(Set.of(responsableRole)));

        ResponsableAccount account = new ResponsableAccount();
        account.setUserId(responsableId);
        account.setUser(actor);
        account.setActive(true);
        account.setDepartment(department);
        account.setAssignedCategories(new HashSet<>(Set.of("TECHNIQUE")));

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setStatus(TicketStatus.DIAGNOSTIC_EN_COURS);
        ticket.setAssignedResponsable(actor);

        when(responsableAccountRepository.findById(responsableId)).thenReturn(Optional.of(account));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.takeCharge(responsableId, ticketId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("takeCharge - Signalement hors périmètre lève 404 Not Found")
    void takeCharge_outsidePerimeter_shouldThrow404() {
        UUID responsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Role responsableRole = new Role();
        responsableRole.setCode("RESPONSABLE");

        AppUser actor = new AppUser();
        actor.setId(responsableId);
        actor.setStatus(AccountStatus.ACTIF);
        actor.setRoles(new HashSet<>(Set.of(responsableRole)));

        ResponsableAccount account = new ResponsableAccount();
        account.setUserId(responsableId);
        account.setUser(actor);
        account.setActive(true);
        account.setDepartment(department);
        account.setAssignedCategories(new HashSet<>(Set.of("RH")));

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setDepartment(department);
        ticket.setCategory(category); // TECHNIQUE (hors périmètre RH)
        ticket.setStatus(TicketStatus.EN_ATTENTE);

        when(responsableAccountRepository.findById(responsableId)).thenReturn(Optional.of(account));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.takeCharge(responsableId, ticketId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("submitForValidation - Succès : passe en ATTENTE_VALIDATION et enregistre l'événement avec note")
    void submitForValidation_shouldUpdateStatusAndRecordEventWithNote() {
        UUID responsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Role responsableRole = new Role();
        responsableRole.setCode("RESPONSABLE");

        AppUser actor = new AppUser();
        actor.setId(responsableId);
        actor.setStatus(AccountStatus.ACTIF);
        actor.setRoles(new HashSet<>(Set.of(responsableRole)));

        ResponsableAccount account = new ResponsableAccount();
        account.setUserId(responsableId);
        account.setUser(actor);
        account.setActive(true);
        account.setDepartment(department);
        account.setAssignedCategories(new HashSet<>(Set.of("TECHNIQUE")));
        account.setConfidentialHabilitation(false);

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setReference("SIG-2026-000001");
        ticket.setTitle("Problème réseau");
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setPriority(TicketPriority.MOYENNE);
        ticket.setStatus(TicketStatus.DIAGNOSTIC_EN_COURS);
        ticket.setAssignedResponsable(actor);
        ticket.setConfidential(false);
        ticket.setCreatedAt(Instant.parse("2026-11-04T10:00:00Z"));

        UpdateTicketStatusRequest request = new UpdateTicketStatusRequest()
                .status(UpdateTicketStatusRequest.StatusEnum.ATTENTE_VALIDATION)
                .note("Le problème de connexion a été corrigé et validé.");

        when(responsableAccountRepository.findById(responsableId)).thenReturn(Optional.of(account));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);

        TicketSummary summary = ticketService.submitForValidation(responsableId, ticketId, request);

        assertThat(summary.getStatus()).isEqualTo(TicketSummary.StatusEnum.ATTENTE_VALIDATION);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ATTENTE_VALIDATION);

        ArgumentCaptor<TicketEvent> eventCaptor = ArgumentCaptor.forClass(TicketEvent.class);
        verify(ticketEventRepository).save(eventCaptor.capture());
        TicketEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getTicket()).isSameAs(ticket);
        assertThat(savedEvent.getActor()).isSameAs(actor);
        assertThat(savedEvent.getEventType()).isEqualTo("SOUMISSION_VALIDATION");
        assertThat(savedEvent.getOldStatus()).isEqualTo(TicketStatus.DIAGNOSTIC_EN_COURS);
        assertThat(savedEvent.getNewStatus()).isEqualTo(TicketStatus.ATTENTE_VALIDATION);
        assertThat(savedEvent.getNote()).isEqualTo("Le problème de connexion a été corrigé et validé.");
    }

    @Test
    @DisplayName("submitForValidation - Responsable non attribué lève 404 Not Found")
    void submitForValidation_wrongResponsable_shouldThrow404() {
        UUID responsableId = UUID.randomUUID();
        UUID otherResponsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Role responsableRole = new Role();
        responsableRole.setCode("RESPONSABLE");

        AppUser actor = new AppUser();
        actor.setId(responsableId);
        actor.setStatus(AccountStatus.ACTIF);
        actor.setRoles(new HashSet<>(Set.of(responsableRole)));

        AppUser otherActor = new AppUser();
        otherActor.setId(otherResponsableId);

        ResponsableAccount account = new ResponsableAccount();
        account.setUserId(responsableId);
        account.setUser(actor);
        account.setActive(true);
        account.setDepartment(department);
        account.setAssignedCategories(new HashSet<>(Set.of("TECHNIQUE")));

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setStatus(TicketStatus.DIAGNOSTIC_EN_COURS);
        ticket.setAssignedResponsable(otherActor);

        UpdateTicketStatusRequest request = new UpdateTicketStatusRequest()
                .status(UpdateTicketStatusRequest.StatusEnum.ATTENTE_VALIDATION)
                .note("Le problème de connexion a été corrigé et validé.");

        when(responsableAccountRepository.findById(responsableId)).thenReturn(Optional.of(account));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.submitForValidation(responsableId, ticketId, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("submitForValidation - Transition depuis un statut autre que DIAGNOSTIC_EN_COURS lève 409 Conflict")
    void submitForValidation_invalidCurrentStatus_shouldThrow409() {
        UUID responsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Role responsableRole = new Role();
        responsableRole.setCode("RESPONSABLE");

        AppUser actor = new AppUser();
        actor.setId(responsableId);
        actor.setStatus(AccountStatus.ACTIF);
        actor.setRoles(new HashSet<>(Set.of(responsableRole)));

        ResponsableAccount account = new ResponsableAccount();
        account.setUserId(responsableId);
        account.setUser(actor);
        account.setActive(true);
        account.setDepartment(department);
        account.setAssignedCategories(new HashSet<>(Set.of("TECHNIQUE")));

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setStatus(TicketStatus.ATTENTE_VALIDATION);
        ticket.setAssignedResponsable(actor);

        UpdateTicketStatusRequest request = new UpdateTicketStatusRequest()
                .status(UpdateTicketStatusRequest.StatusEnum.ATTENTE_VALIDATION)
                .note("Le problème de connexion a été corrigé et validé.");

        when(responsableAccountRepository.findById(responsableId)).thenReturn(Optional.of(account));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.submitForValidation(responsableId, ticketId, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("reviewTicket - ACCEPTER_SOLUTION passe le signalement à RESOLU")
    void reviewTicket_acceptSolution_shouldSetStatusResolu() {
        UUID ticketId = UUID.randomUUID();

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setReference("SIG-2026-000001");
        ticket.setTitle("Problème réseau");
        ticket.setEmployee(employee);
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setPriority(TicketPriority.MOYENNE);
        ticket.setStatus(TicketStatus.ATTENTE_VALIDATION);
        ticket.setAssignedResponsable(new AppUser());
        ticket.setCreatedAt(Instant.parse("2026-11-04T10:00:00Z"));

        ReviewTicketRequest request = new ReviewTicketRequest()
                .action(ReviewTicketRequest.ActionEnum.ACCEPTER_SOLUTION);

        when(userRepository.findByIdWithRoles(employeeId)).thenReturn(Optional.of(employee));
        when(ticketRepository.findOwnedByIdForUpdate(ticketId, employeeId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);

        TicketSummary summary = ticketService.reviewTicket(employeeId, ticketId, request);

        assertThat(summary.getStatus()).isEqualTo(TicketSummary.StatusEnum.RESOLU);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLU);

        ArgumentCaptor<TicketEvent> eventCaptor = ArgumentCaptor.forClass(TicketEvent.class);
        verify(ticketEventRepository).save(eventCaptor.capture());
        TicketEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getEventType()).isEqualTo("SOLUTION_ACCEPTEE");
        assertThat(savedEvent.getOldStatus()).isEqualTo(TicketStatus.ATTENTE_VALIDATION);
        assertThat(savedEvent.getNewStatus()).isEqualTo(TicketStatus.RESOLU);
        assertThat(savedEvent.getNote()).isNull();
    }

    @Test
    @DisplayName("reviewTicket - REFUSER_SOLUTION passe le signalement à ROUVERT avec note obligatoire")
    void reviewTicket_refuseSolution_shouldSetStatusRouvertWithNote() {
        UUID ticketId = UUID.randomUUID();

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setReference("SIG-2026-000001");
        ticket.setTitle("Problème réseau");
        ticket.setEmployee(employee);
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setPriority(TicketPriority.MOYENNE);
        ticket.setStatus(TicketStatus.ATTENTE_VALIDATION);
        ticket.setAssignedResponsable(new AppUser());
        ticket.setCreatedAt(Instant.parse("2026-11-04T10:00:00Z"));

        ReviewTicketRequest request = new ReviewTicketRequest()
                .action(ReviewTicketRequest.ActionEnum.REFUSER_SOLUTION)
                .note("Le problème de connexion persiste malgré l'intervention.");

        when(userRepository.findByIdWithRoles(employeeId)).thenReturn(Optional.of(employee));
        when(ticketRepository.findOwnedByIdForUpdate(ticketId, employeeId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);

        TicketSummary summary = ticketService.reviewTicket(employeeId, ticketId, request);

        assertThat(summary.getStatus()).isEqualTo(TicketSummary.StatusEnum.ROUVERT);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ROUVERT);

        ArgumentCaptor<TicketEvent> eventCaptor = ArgumentCaptor.forClass(TicketEvent.class);
        verify(ticketEventRepository).save(eventCaptor.capture());
        TicketEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getEventType()).isEqualTo("SOLUTION_REFUSEE");
        assertThat(savedEvent.getOldStatus()).isEqualTo(TicketStatus.ATTENTE_VALIDATION);
        assertThat(savedEvent.getNewStatus()).isEqualTo(TicketStatus.ROUVERT);
        assertThat(savedEvent.getNote()).isEqualTo("Le problème de connexion persiste malgré l'intervention.");
    }

    @Test
    @DisplayName("reviewTicket - REFUSER_SOLUTION sans note suffisante lève 400 Bad Request")
    void reviewTicket_refuseWithoutNote_shouldThrow400() {
        UUID ticketId = UUID.randomUUID();

        ReviewTicketRequest request = new ReviewTicketRequest()
                .action(ReviewTicketRequest.ActionEnum.REFUSER_SOLUTION)
                .note("Court");

        assertThatThrownBy(() -> ticketService.reviewTicket(employeeId, ticketId, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // =========================================================================
    // Étape 40 — resumeTicket
    // =========================================================================

    @Test
    @DisplayName("resumeTicket - Passe de ROUVERT à DIAGNOSTIC_EN_COURS et crée l'événement REPRISE_TRAITEMENT")
    void resumeTicket_shouldTransitionToDiagnosticEnCoursAndCreateEvent() {
        UUID responsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Role responsableRole = new Role();
        responsableRole.setCode("RESPONSABLE");

        AppUser responsable = new AppUser();
        responsable.setId(responsableId);
        responsable.setStatus(AccountStatus.ACTIF);
        responsable.setDepartment(department);
        responsable.setRoles(new HashSet<>(Set.of(responsableRole)));

        ResponsableAccount responsableAccount = new ResponsableAccount();
        responsableAccount.setUserId(responsableId);
        responsableAccount.setUser(responsable);
        responsableAccount.setDepartment(department);
        responsableAccount.setActive(true);
        responsableAccount.setConfidentialHabilitation(false);
        responsableAccount.setAssignedCategories(new HashSet<>(Set.of("TECHNIQUE")));

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setReference("SIG-2026-000001");
        ticket.setTitle("Problème réseau");
        ticket.setDescription("Connexion coupée");
        ticket.setCategory(category);
        ticket.setDepartment(department);
        ticket.setPriority(TicketPriority.MOYENNE);
        ticket.setStatus(TicketStatus.ROUVERT);
        ticket.setConfidential(false);
        ticket.setEmployee(employee);
        ticket.setAssignedResponsable(responsable);
        ticket.setCreatedAt(Instant.parse("2026-11-04T10:00:00Z"));
        ticket.setUpdatedAt(Instant.parse("2026-11-04T10:00:00Z"));

        when(responsableAccountRepository.findById(responsableId)).thenReturn(Optional.of(responsableAccount));
        when(userRepository.findByIdWithRoles(responsableId)).thenReturn(Optional.of(responsable));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);

        TicketSummary summary = ticketService.resumeTicket(responsableId, ticketId);

        assertThat(summary.getStatus()).isEqualTo(TicketSummary.StatusEnum.DIAGNOSTIC_EN_COURS);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.DIAGNOSTIC_EN_COURS);

        ArgumentCaptor<TicketEvent> eventCaptor = ArgumentCaptor.forClass(TicketEvent.class);
        verify(ticketEventRepository).save(eventCaptor.capture());
        TicketEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getEventType()).isEqualTo("REPRISE_TRAITEMENT");
        assertThat(savedEvent.getOldStatus()).isEqualTo(TicketStatus.ROUVERT);
        assertThat(savedEvent.getNewStatus()).isEqualTo(TicketStatus.DIAGNOSTIC_EN_COURS);
        assertThat(savedEvent.getActor()).isEqualTo(responsable);
    }

    @Test
    @DisplayName("resumeTicket - Signalement non ROUVERT lève 409 Conflict")
    void resumeTicket_whenStatusNotRouvert_shouldThrow409() {
        UUID responsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Role responsableRole = new Role();
        responsableRole.setCode("RESPONSABLE");

        AppUser responsable = new AppUser();
        responsable.setId(responsableId);
        responsable.setStatus(AccountStatus.ACTIF);
        responsable.setDepartment(department);
        responsable.setRoles(new HashSet<>(Set.of(responsableRole)));

        ResponsableAccount responsableAccount = new ResponsableAccount();
        responsableAccount.setUserId(responsableId);
        responsableAccount.setUser(responsable);
        responsableAccount.setDepartment(department);
        responsableAccount.setActive(true);
        responsableAccount.setConfidentialHabilitation(false);
        responsableAccount.setAssignedCategories(new HashSet<>(Set.of("TECHNIQUE")));

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setReference("SIG-2026-000001");
        ticket.setCategory(category);
        ticket.setDepartment(department);
        ticket.setStatus(TicketStatus.RESOLU);
        ticket.setConfidential(false);
        ticket.setAssignedResponsable(responsable);

        when(responsableAccountRepository.findById(responsableId)).thenReturn(Optional.of(responsableAccount));
        when(userRepository.findByIdWithRoles(responsableId)).thenReturn(Optional.of(responsable));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.resumeTicket(responsableId, ticketId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("resumeTicket - Autre responsable non attribué lève 404 Not Found")
    void resumeTicket_whenNotAssignedResponsable_shouldThrow404() {
        UUID responsableId = UUID.randomUUID();
        UUID otherResponsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Role responsableRole = new Role();
        responsableRole.setCode("RESPONSABLE");

        AppUser responsable = new AppUser();
        responsable.setId(responsableId);
        responsable.setStatus(AccountStatus.ACTIF);
        responsable.setDepartment(department);
        responsable.setRoles(new HashSet<>(Set.of(responsableRole)));

        AppUser otherResponsable = new AppUser();
        otherResponsable.setId(otherResponsableId);

        ResponsableAccount responsableAccount = new ResponsableAccount();
        responsableAccount.setUserId(responsableId);
        responsableAccount.setUser(responsable);
        responsableAccount.setDepartment(department);
        responsableAccount.setActive(true);
        responsableAccount.setConfidentialHabilitation(false);
        responsableAccount.setAssignedCategories(new HashSet<>(Set.of("TECHNIQUE")));

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setCategory(category);
        ticket.setDepartment(department);
        ticket.setStatus(TicketStatus.ROUVERT);
        ticket.setConfidential(false);
        ticket.setAssignedResponsable(otherResponsable);

        when(responsableAccountRepository.findById(responsableId)).thenReturn(Optional.of(responsableAccount));
        when(userRepository.findByIdWithRoles(responsableId)).thenReturn(Optional.of(responsable));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.resumeTicket(responsableId, ticketId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // =========================================================================
    // Étape 41 — closeTicket
    // =========================================================================

    @Test
    @DisplayName("closeTicket - Passe de RESOLU à CLOTURE et crée l'événement CLOTURE_SIGNALEMENT")
    void closeTicket_shouldTransitionFromResoluToClotureAndCreateEvent() {
        UUID ticketId = UUID.randomUUID();

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setReference("SIG-2026-000001");
        ticket.setTitle("Problème réseau");
        ticket.setEmployee(employee);
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setPriority(TicketPriority.MOYENNE);
        ticket.setStatus(TicketStatus.RESOLU);
        ticket.setAssignedResponsable(new AppUser());
        ticket.setCreatedAt(Instant.parse("2026-11-04T10:00:00Z"));

        when(userRepository.findByIdWithRoles(employeeId)).thenReturn(Optional.of(employee));
        when(ticketRepository.findOwnedByIdForUpdate(ticketId, employeeId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);

        TicketSummary summary = ticketService.closeTicket(employeeId, ticketId);

        assertThat(summary.getStatus()).isEqualTo(TicketSummary.StatusEnum.CLOTURE);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOTURE);

        ArgumentCaptor<TicketEvent> eventCaptor = ArgumentCaptor.forClass(TicketEvent.class);
        verify(ticketEventRepository).save(eventCaptor.capture());
        TicketEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getEventType()).isEqualTo("CLOTURE_SIGNALEMENT");
        assertThat(savedEvent.getOldStatus()).isEqualTo(TicketStatus.RESOLU);
        assertThat(savedEvent.getNewStatus()).isEqualTo(TicketStatus.CLOTURE);
        assertThat(savedEvent.getActor()).isEqualTo(employee);
    }

    @Test
    @DisplayName("closeTicket - Signalement non RESOLU lève 409 Conflict")
    void closeTicket_whenStatusNotResolu_shouldThrow409() {
        UUID ticketId = UUID.randomUUID();

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setEmployee(employee);
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setStatus(TicketStatus.ATTENTE_VALIDATION);
        ticket.setAssignedResponsable(new AppUser());

        when(userRepository.findByIdWithRoles(employeeId)).thenReturn(Optional.of(employee));
        when(ticketRepository.findOwnedByIdForUpdate(ticketId, employeeId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.closeTicket(employeeId, ticketId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("closeTicket - Signalement introuvable ou appartenant à un autre employé lève 404 Not Found")
    void closeTicket_whenNotFoundOrNotOwned_shouldThrow404() {
        UUID ticketId = UUID.randomUUID();

        when(userRepository.findByIdWithRoles(employeeId)).thenReturn(Optional.of(employee));
        when(ticketRepository.findOwnedByIdForUpdate(ticketId, employeeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.closeTicket(employeeId, ticketId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // =========================================================================
    // Étape 42 — getMyTicketHistory & getResponsableTicketHistory
    // =========================================================================

    @Test
    @DisplayName("getMyTicketHistory - Retourne l'historique chronologique si l'employé est propriétaire")
    void getMyTicketHistory_whenOwner_shouldReturnEvents() {
        UUID ticketId = UUID.randomUUID();
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();

        TicketEvent event1 = new TicketEvent();
        event1.setId(eventId1);
        event1.setEventType("CREATION_SIGNALEMENT");
        event1.setOldStatus(null);
        event1.setNewStatus(TicketStatus.EN_ATTENTE);
        event1.setCreatedAt(Instant.parse("2026-11-04T08:30:00Z"));

        TicketEvent event2 = new TicketEvent();
        event2.setId(eventId2);
        event2.setEventType("PRISE_EN_CHARGE");
        event2.setOldStatus(TicketStatus.EN_ATTENTE);
        event2.setNewStatus(TicketStatus.DIAGNOSTIC_EN_COURS);
        event2.setCreatedAt(Instant.parse("2026-11-04T09:15:00Z"));

        when(ticketRepository.existsByIdAndEmployee_Id(ticketId, employeeId)).thenReturn(true);
        when(ticketEventRepository.findHistoryByTicketId(ticketId)).thenReturn(List.of(event1, event2));

        List<TicketHistoryEvent> history = ticketService.getMyTicketHistory(employeeId, ticketId);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getId()).isEqualTo(eventId1.toString());
        assertThat(history.get(0).getEventType()).isEqualTo("CREATION_SIGNALEMENT");
        assertThat(history.get(0).getActorLabel()).isEqualTo("Employé");
        assertThat(history.get(0).getPreviousStatus()).isNull();
        assertThat(history.get(0).getNewStatus()).isEqualTo("EN_ATTENTE");
        assertThat(history.get(0).getOccurredAt()).isEqualTo("2026-11-04T08:30:00Z");

        assertThat(history.get(1).getId()).isEqualTo(eventId2.toString());
        assertThat(history.get(1).getEventType()).isEqualTo("PRISE_EN_CHARGE");
        assertThat(history.get(1).getActorLabel()).isEqualTo("Responsable");
        assertThat(history.get(1).getPreviousStatus()).isEqualTo("EN_ATTENTE");
        assertThat(history.get(1).getNewStatus()).isEqualTo("DIAGNOSTIC_EN_COURS");
    }

    @Test
    @DisplayName("getMyTicketHistory - Employé non propriétaire lève 404 Not Found")
    void getMyTicketHistory_whenNotOwner_shouldThrow404() {
        UUID ticketId = UUID.randomUUID();

        when(ticketRepository.existsByIdAndEmployee_Id(ticketId, employeeId)).thenReturn(false);

        assertThatThrownBy(() -> ticketService.getMyTicketHistory(employeeId, ticketId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getResponsableTicketHistory - Retourne l'historique si le responsable est attribué et habilité")
    void getResponsableTicketHistory_whenAssignedAndAuthorized_shouldReturnEvents() {
        UUID responsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        AppUser responsable = new AppUser();
        responsable.setId(responsableId);

        ResponsableAccount responsableAccount = new ResponsableAccount();
        responsableAccount.setUserId(responsableId);
        responsableAccount.setUser(responsable);
        responsableAccount.setDepartment(department);
        responsableAccount.setActive(true);
        responsableAccount.setConfidentialHabilitation(false);
        responsableAccount.setAssignedCategories(new HashSet<>(Set.of("TECHNIQUE")));

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setConfidential(false);
        ticket.setAssignedResponsable(responsable);

        TicketEvent event = new TicketEvent();
        event.setId(eventId);
        event.setEventType("SOUMISSION_VALIDATION");
        event.setOldStatus(TicketStatus.DIAGNOSTIC_EN_COURS);
        event.setNewStatus(TicketStatus.ATTENTE_VALIDATION);
        event.setNote("Solution appliquée");
        event.setCreatedAt(Instant.parse("2026-11-04T11:20:00Z"));

        when(responsableAccountRepository.findById(responsableId)).thenReturn(Optional.of(responsableAccount));
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketEventRepository.findHistoryByTicketId(ticketId)).thenReturn(List.of(event));

        List<TicketHistoryEvent> history = ticketService.getResponsableTicketHistory(responsableId, ticketId);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getEventType()).isEqualTo("SOUMISSION_VALIDATION");
        assertThat(history.get(0).getActorLabel()).isEqualTo("Responsable");
        assertThat(history.get(0).getNote()).isEqualTo("Solution appliquée");
    }

    @Test
    @DisplayName("getResponsableTicketHistory - Responsable non attribué lève 404 Not Found")
    void getResponsableTicketHistory_whenNotAssigned_shouldThrow404() {
        UUID responsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        ResponsableAccount responsableAccount = new ResponsableAccount();
        responsableAccount.setUserId(responsableId);
        responsableAccount.setDepartment(department);
        responsableAccount.setActive(true);
        responsableAccount.setConfidentialHabilitation(false);
        responsableAccount.setAssignedCategories(new HashSet<>(Set.of("TECHNIQUE")));

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setConfidential(false);
        ticket.setAssignedResponsable(null);

        when(responsableAccountRepository.findById(responsableId)).thenReturn(Optional.of(responsableAccount));
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.getResponsableTicketHistory(responsableId, ticketId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
