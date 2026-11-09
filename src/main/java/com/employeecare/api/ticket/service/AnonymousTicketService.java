package com.employeecare.api.ticket.service;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.generated.models.AnonymousTicketCreatedResponse;
import com.employeecare.api.generated.models.CreateAnonymousTicketRequest;
import com.employeecare.api.notification.event.TicketResponsibleNotificationEvent;
import com.employeecare.api.ticket.entity.Ticket;
import com.employeecare.api.ticket.entity.TicketCategory;
import com.employeecare.api.ticket.entity.TicketEvent;
import com.employeecare.api.ticket.entity.TicketPriority;
import com.employeecare.api.ticket.entity.TicketStatus;
import com.employeecare.api.ticket.repository.TicketCategoryRepository;
import com.employeecare.api.ticket.repository.TicketEventRepository;
import com.employeecare.api.ticket.repository.TicketRepository;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Year;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class AnonymousTicketService {

    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository categoryRepository;
    private final TicketEventRepository eventRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    public AnonymousTicketService(
            TicketRepository ticketRepository,
            TicketCategoryRepository categoryRepository,
            TicketEventRepository eventRepository,
            EntityManager entityManager,
            JdbcTemplate jdbcTemplate,
            ApplicationEventPublisher eventPublisher
    ) {
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.eventRepository = eventRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AnonymousTicketCreatedResponse create(
            CreateAnonymousTicketRequest request
    ) {

        // 1. Vérifier la requête.

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Les informations du signalement sont obligatoires."
            );
        }

        String title = request.getTitle() == null
                ? ""
                : request.getTitle().trim();

        String description = request.getDescription() == null
                ? ""
                : request.getDescription().trim();

        if (title.length() < 5 || title.length() > 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Titre invalide."
            );
        }

        if (description.length() < 10 || description.length() > 5000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Description invalide."
            );
        }

        // 2. Vérifier la catégorie.

        String categoryCode = request.getCategoryCode() == null
                ? ""
                : request.getCategoryCode()
                        .trim()
                        .toUpperCase(Locale.ROOT);

        TicketCategory category = categoryRepository
                .findById(categoryCode)
                .filter(TicketCategory::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Catégorie inexistante ou désactivée."
                ));

        // 3. Vérifier le département destinataire.

        UUID departmentId = request.getDepartmentId();

        if (departmentId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Département obligatoire."
            );
        }

        Department department = entityManager.find(
                Department.class,
                departmentId
        );

        if (department == null || !department.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Département inexistant ou inactif."
            );
        }

        // 4. Générer une référence unique.

        Long sequence = jdbcTemplate.queryForObject(
                "SELECT nextval('ticket_reference_seq')",
                Long.class
        );

        int year = Year.now(
                ZoneId.of("Africa/Douala")
        ).getValue();

        String reference = String.format(
                "SIG-%d-%06d",
                year,
                sequence
        );

        // 5. Construire le signalement anonyme.

        Ticket ticket = new Ticket();

        ticket.setReference(reference);
        ticket.setTitle(title);
        ticket.setDescription(description);

        // Aucun utilisateur associé.
        ticket.setEmployee(null);
        ticket.setAnonymous(true);

        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setPriority(TicketPriority.MOYENNE);
        ticket.setStatus(TicketStatus.EN_ATTENTE);

        // Les signalements anonymes sont confidentiels.
        ticket.setConfidential(true);

        Ticket savedTicket = ticketRepository.saveAndFlush(ticket);

        // 6. Générer un code secret aléatoire.

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String accessCode = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        // 7. Enregistrer uniquement son empreinte.

        String accessCodeHash = sha256(accessCode);

        jdbcTemplate.update(
                """
                INSERT INTO anonymous_ticket_access
                (ticket_id, access_key_hash)
                VALUES (?, ?)
                """,
                savedTicket.getId(),
                accessCodeHash
        );

        // 8. Enregistrer l'événement de création.

        TicketEvent event = new TicketEvent();

        event.setTicket(savedTicket);
        event.setActor(null);
        event.setActorKind("ANONYMOUS");
        event.setEventType(
                "CREATION_SIGNALEMENT_ANONYME"
        );
        event.setOldStatus(null);
        event.setNewStatus(TicketStatus.EN_ATTENTE);

        eventRepository.save(event);

        // 9. Notifier les responsables habilités.

        eventPublisher.publishEvent(
                new TicketResponsibleNotificationEvent(
                        savedTicket.getId(),
                        TicketResponsibleNotificationEvent.Type
                                .NOUVEAU_SIGNALEMENT
                )
        );

        // 10. Retourner les informations de suivi.

        AnonymousTicketCreatedResponse response =
                new AnonymousTicketCreatedResponse();

        response.setTicketId(savedTicket.getId());
        response.setReference(savedTicket.getReference());
        response.setAccessCode(accessCode);

        return response;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 indisponible.",
                    exception
            );
        }
    }
}
