package com.employeecare.api.ticket.service;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.generated.models.AnonymousTicketActionResponse;
import com.employeecare.api.generated.models.AnonymousTicketCloseRequest;
import com.employeecare.api.generated.models.AnonymousTicketCreatedResponse;
import com.employeecare.api.generated.models.AnonymousTicketLookupRequest;
import com.employeecare.api.generated.models.AnonymousTicketLookupResponse;
import com.employeecare.api.generated.models.AnonymousTicketReviewRequest;
import com.employeecare.api.generated.models.CreateAnonymousTicketRequest;
import com.employeecare.api.notification.event.TicketResponsibleNotificationEvent;
import com.employeecare.api.ticket.entity.Ticket;
import com.employeecare.api.ticket.entity.TicketCategory;
import com.employeecare.api.ticket.entity.TicketEvent;
import com.employeecare.api.ticket.entity.TicketStatus;
import com.employeecare.api.ticket.repository.TicketCategoryRepository;
import com.employeecare.api.ticket.repository.TicketEventRepository;
import com.employeecare.api.ticket.repository.TicketRepository;
import com.employeecare.api.user.entity.AppUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnonymousTicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketCategoryRepository categoryRepository;

    @Mock
    private TicketEventRepository eventRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AnonymousTicketService anonymousTicketService;

    @Test
    @DisplayName("create - Crée un signalement anonyme, stocke le hash SHA-256 du code secret et publie la notification")
    void create_validRequest_shouldCreateAnonymousTicketAndStoreHash() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        CreateAnonymousTicketRequest request = new CreateAnonymousTicketRequest()
                .title("Situation de harcèlement professionnel")
                .description("Je souhaite signaler une situation professionnelle préoccupante nécessitant un traitement confidentiel.")
                .categoryCode("RH")
                .departmentId(departmentId);

        TicketCategory category = new TicketCategory();
        category.setCode("RH");
        category.setLabel("Ressources Humaines");
        category.setActive(true);

        Department department = new Department();
        department.setId(departmentId);
        department.setActive(true);

        when(categoryRepository.findById("RH")).thenReturn(Optional.of(category));
        when(entityManager.find(Department.class, departmentId)).thenReturn(department);
        when(jdbcTemplate.queryForObject("SELECT nextval('ticket_reference_seq')", Long.class)).thenReturn(125L);
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.setId(ticketId);
            return t;
        });

        AnonymousTicketCreatedResponse response = anonymousTicketService.create(request);

        assertThat(response.getTicketId()).isEqualTo(ticketId);
        assertThat(response.getReference()).isEqualTo("SIG-2026-000125");
        assertThat(response.getAccessCode()).isNotBlank();

        // Vérifier le ticket sauvegardé
        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).saveAndFlush(ticketCaptor.capture());
        Ticket savedTicket = ticketCaptor.getValue();
        assertThat(savedTicket.getEmployee()).isNull();
        assertThat(savedTicket.isAnonymous()).isTrue();
        assertThat(savedTicket.isConfidential()).isTrue();
        assertThat(savedTicket.getStatus()).isEqualTo(TicketStatus.EN_ATTENTE);

        // Vérifier l'empreinte SHA-256 enregistrée
        byte[] expectedHashBytes = MessageDigest.getInstance("SHA-256")
                .digest(response.getAccessCode().getBytes(StandardCharsets.UTF_8));
        String expectedHash = HexFormat.of().formatHex(expectedHashBytes);
        verify(jdbcTemplate).update(anyString(), eq(ticketId), eq(expectedHash));

        // Vérifier l'événement d'historique
        ArgumentCaptor<TicketEvent> eventCaptor = ArgumentCaptor.forClass(TicketEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        TicketEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getActor()).isNull();
        assertThat(savedEvent.getActorKind()).isEqualTo("ANONYMOUS");
        assertThat(savedEvent.getEventType()).isEqualTo("CREATION_SIGNALEMENT_ANONYME");

        // Vérifier la notification aux responsables
        ArgumentCaptor<TicketResponsibleNotificationEvent> notifCaptor =
                ArgumentCaptor.forClass(TicketResponsibleNotificationEvent.class);
        verify(eventPublisher).publishEvent(notifCaptor.capture());
        assertThat(notifCaptor.getValue().ticketId()).isEqualTo(ticketId);
        assertThat(notifCaptor.getValue().type())
                .isEqualTo(TicketResponsibleNotificationEvent.Type.NOUVEAU_SIGNALEMENT);
    }

    @Test
    @DisplayName("create - Catégorie inexistante lève 400 Bad Request")
    void create_invalidCategory_shouldThrow400() {
        CreateAnonymousTicketRequest request = new CreateAnonymousTicketRequest()
                .title("Situation de harcèlement")
                .description("Description détaillée de la situation professionnelle.")
                .categoryCode("INCONNUE")
                .departmentId(UUID.randomUUID());

        when(categoryRepository.findById("INCONNUE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> anonymousTicketService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("lookupAnonymousTicket - Retourne le statut et la solution proposée lorsque le code secret est valide")
    void lookupAnonymousTicket_whenValidAndAttenteValidation_shouldReturnResponseWithSolutionNote() throws Exception {
        UUID ticketId = UUID.randomUUID();
        String accessCode = "gV8nS6AqK7R3xB5tW2pL9mH4cY1dF0uZ8jN6eQ3sT5A";
        byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                .digest(accessCode.getBytes(StandardCharsets.UTF_8));
        String storedHash = HexFormat.of().formatHex(hashBytes);

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setReference("SIG-2026-000125");
        ticket.setTitle("Problème professionnel confidentiel");
        ticket.setAnonymous(true);
        ticket.setStatus(TicketStatus.ATTENTE_VALIDATION);
        ticket.setCreatedAt(Instant.parse("2026-11-09T12:30:00Z"));
        ticket.setUpdatedAt(Instant.parse("2026-11-09T14:00:00Z"));

        TicketEvent validationEvent = new TicketEvent();
        validationEvent.setNote("Des mesures correctives ont été prises.");

        AnonymousTicketLookupRequest request = new AnonymousTicketLookupRequest()
                .reference("SIG-2026-000125")
                .accessCode(accessCode);

        when(ticketRepository.findByReferenceAndAnonymousTrue("SIG-2026-000125"))
                .thenReturn(Optional.of(ticket));
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(ticketId)))
                .thenReturn(storedHash);
        when(eventRepository.findFirstByTicket_IdAndEventTypeOrderByCreatedAtDescIdDesc(
                ticketId, "SOUMISSION_VALIDATION"
        )).thenReturn(Optional.of(validationEvent));

        AnonymousTicketLookupResponse response = anonymousTicketService.lookupAnonymousTicket(request);

        assertThat(response.getReference()).isEqualTo("SIG-2026-000125");
        assertThat(response.getTitle()).isEqualTo("Problème professionnel confidentiel");
        assertThat(response.getStatus()).isEqualTo("ATTENTE_VALIDATION");
        assertThat(response.getSolutionNote()).isEqualTo("Des mesures correctives ont été prises.");
    }

    @Test
    @DisplayName("lookupAnonymousTicket - Lève 404 Not Found si le code secret est incorrect")
    void lookupAnonymousTicket_whenInvalidCode_shouldThrow404() throws Exception {
        UUID ticketId = UUID.randomUUID();
        String realCode = "gV8nS6AqK7R3xB5tW2pL9mH4cY1dF0uZ8jN6eQ3sT5A";
        byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                .digest(realCode.getBytes(StandardCharsets.UTF_8));
        String storedHash = HexFormat.of().formatHex(hashBytes);

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setReference("SIG-2026-000125");
        ticket.setAnonymous(true);

        AnonymousTicketLookupRequest request = new AnonymousTicketLookupRequest()
                .reference("SIG-2026-000125")
                .accessCode("MAUVAIS_CODE_SECRET");

        when(ticketRepository.findByReferenceAndAnonymousTrue("SIG-2026-000125"))
                .thenReturn(Optional.of(ticket));
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(ticketId)))
                .thenReturn(storedHash);

        assertThatThrownBy(() -> anonymousTicketService.lookupAnonymousTicket(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("reviewAnonymousTicket - Accepter la solution passe au statut RESOLU avec actorKind ANONYMOUS")
    void reviewAnonymousTicket_whenAccept_shouldSetResolu() throws Exception {
        UUID ticketId = UUID.randomUUID();
        String accessCode = "gV8nS6AqK7R3xB5tW2pL9mH4cY1dF0uZ8jN6eQ3sT5A";
        byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                .digest(accessCode.getBytes(StandardCharsets.UTF_8));
        String storedHash = HexFormat.of().formatHex(hashBytes);

        AppUser responsable = new AppUser();
        responsable.setId(UUID.randomUUID());

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setReference("SIG-2026-000125");
        ticket.setAnonymous(true);
        ticket.setEmployee(null);
        ticket.setAssignedResponsable(responsable);
        ticket.setStatus(TicketStatus.ATTENTE_VALIDATION);

        AnonymousTicketReviewRequest request = new AnonymousTicketReviewRequest()
                .reference("SIG-2026-000125")
                .accessCode(accessCode)
                .action(AnonymousTicketReviewRequest.ActionEnum.ACCEPTER_SOLUTION)
                .note("La solution me convient.");

        when(ticketRepository.findByReferenceAndAnonymousTrue("SIG-2026-000125"))
                .thenReturn(Optional.of(ticket));
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(ticketId)))
                .thenReturn(storedHash);
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));

        AnonymousTicketActionResponse response = anonymousTicketService.reviewAnonymousTicket(request);

        assertThat(response.getReference()).isEqualTo("SIG-2026-000125");
        assertThat(response.getStatus()).isEqualTo("RESOLU");

        ArgumentCaptor<TicketEvent> eventCaptor = ArgumentCaptor.forClass(TicketEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        TicketEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getActor()).isNull();
        assertThat(savedEvent.getActorKind()).isEqualTo("ANONYMOUS");
        assertThat(savedEvent.getEventType()).isEqualTo("SOLUTION_ACCEPTEE");
        assertThat(savedEvent.getOldStatus()).isEqualTo(TicketStatus.ATTENTE_VALIDATION);
        assertThat(savedEvent.getNewStatus()).isEqualTo(TicketStatus.RESOLU);
    }

    @Test
    @DisplayName("reviewAnonymousTicket - Refuser la solution passe au statut ROUVERT et notifie le responsable")
    void reviewAnonymousTicket_whenRefuse_shouldSetRouvertAndNotify() throws Exception {
        UUID ticketId = UUID.randomUUID();
        String accessCode = "gV8nS6AqK7R3xB5tW2pL9mH4cY1dF0uZ8jN6eQ3sT5A";
        byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                .digest(accessCode.getBytes(StandardCharsets.UTF_8));
        String storedHash = HexFormat.of().formatHex(hashBytes);

        AppUser responsable = new AppUser();
        responsable.setId(UUID.randomUUID());

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setReference("SIG-2026-000126");
        ticket.setAnonymous(true);
        ticket.setEmployee(null);
        ticket.setAssignedResponsable(responsable);
        ticket.setStatus(TicketStatus.ATTENTE_VALIDATION);

        AnonymousTicketReviewRequest request = new AnonymousTicketReviewRequest()
                .reference("SIG-2026-000126")
                .accessCode(accessCode)
                .action(AnonymousTicketReviewRequest.ActionEnum.REFUSER_SOLUTION)
                .note("La situation signalée persiste malgré les mesures prises.");

        when(ticketRepository.findByReferenceAndAnonymousTrue("SIG-2026-000126"))
                .thenReturn(Optional.of(ticket));
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(ticketId)))
                .thenReturn(storedHash);
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));

        AnonymousTicketActionResponse response = anonymousTicketService.reviewAnonymousTicket(request);

        assertThat(response.getReference()).isEqualTo("SIG-2026-000126");
        assertThat(response.getStatus()).isEqualTo("ROUVERT");

        ArgumentCaptor<TicketResponsibleNotificationEvent> notifCaptor =
                ArgumentCaptor.forClass(TicketResponsibleNotificationEvent.class);
        verify(eventPublisher).publishEvent(notifCaptor.capture());
        assertThat(notifCaptor.getValue().ticketId()).isEqualTo(ticketId);
        assertThat(notifCaptor.getValue().type())
                .isEqualTo(TicketResponsibleNotificationEvent.Type.SOLUTION_REFUSEE);
    }

    @Test
    @DisplayName("closeAnonymousTicket - Clôture un signalement RESOLU avec actorKind ANONYMOUS")
    void closeAnonymousTicket_whenResolu_shouldSetCloture() throws Exception {
        UUID ticketId = UUID.randomUUID();
        String accessCode = "gV8nS6AqK7R3xB5tW2pL9mH4cY1dF0uZ8jN6eQ3sT5A";
        byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                .digest(accessCode.getBytes(StandardCharsets.UTF_8));
        String storedHash = HexFormat.of().formatHex(hashBytes);

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setReference("SIG-2026-000125");
        ticket.setAnonymous(true);
        ticket.setEmployee(null);
        ticket.setStatus(TicketStatus.RESOLU);

        AnonymousTicketCloseRequest request = new AnonymousTicketCloseRequest()
                .reference("SIG-2026-000125")
                .accessCode(accessCode);

        when(ticketRepository.findByReferenceAndAnonymousTrue("SIG-2026-000125"))
                .thenReturn(Optional.of(ticket));
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(ticketId)))
                .thenReturn(storedHash);
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));

        AnonymousTicketActionResponse response = anonymousTicketService.closeAnonymousTicket(request);

        assertThat(response.getReference()).isEqualTo("SIG-2026-000125");
        assertThat(response.getStatus()).isEqualTo("CLOTURE");

        ArgumentCaptor<TicketEvent> eventCaptor = ArgumentCaptor.forClass(TicketEvent.class);
        verify(eventRepository).save(eventCaptor.capture());
        TicketEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getActor()).isNull();
        assertThat(savedEvent.getActorKind()).isEqualTo("ANONYMOUS");
        assertThat(savedEvent.getEventType()).isEqualTo("CLOTURE_SIGNALEMENT");
    }
}
