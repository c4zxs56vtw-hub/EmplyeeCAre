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
import com.employeecare.api.ticket.entity.TicketPriority;
import com.employeecare.api.ticket.entity.TicketStatus;
import com.employeecare.api.ticket.repository.TicketCategoryRepository;
import com.employeecare.api.ticket.repository.TicketEventRepository;
import com.employeecare.api.ticket.repository.TicketRepository;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

    @Transactional(readOnly = true)
    public AnonymousTicketLookupResponse lookupAnonymousTicket(
            AnonymousTicketLookupRequest request
    ) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Les informations de suivi sont obligatoires."
            );
        }

        Ticket ticket = verifyAnonymousAccess(
                request.getReference(),
                request.getAccessCode()
        );

        // ==========================================
        // CONSTRUIRE LA REPONSE
        // ==========================================

        AnonymousTicketLookupResponse response =
                new AnonymousTicketLookupResponse();

        response.setReference(
                ticket.getReference()
        );

        response.setTitle(
                ticket.getTitle()
        );

        response.setStatus(
                ticket.getStatus().name()
        );

        response.setCreatedAt(
                OffsetDateTime.ofInstant(
                        ticket.getCreatedAt(),
                        ZoneOffset.UTC
                )
        );

        if (ticket.getUpdatedAt() != null) {
            response.setUpdatedAt(
                    OffsetDateTime.ofInstant(
                            ticket.getUpdatedAt(),
                            ZoneOffset.UTC
                    )
            );
        }

        // ==========================================
        // RECUPERER LA SOLUTION EVENTUELLE
        // ==========================================

        if (ticket.getStatus() == TicketStatus.ATTENTE_VALIDATION) {

            eventRepository
                    .findFirstByTicket_IdAndEventTypeOrderByCreatedAtDescIdDesc(
                            ticket.getId(),
                            "SOUMISSION_VALIDATION"
                    )
                    .ifPresent(event ->
                            response.setSolutionNote(
                                    event.getNote()
                            )
                    );
        }

        return response;
    }

    @Transactional
    public AnonymousTicketActionResponse reviewAnonymousTicket(
            AnonymousTicketReviewRequest request
    ) {

        // ==========================================
        // 1. VERIFIER LA REQUETE
        // ==========================================

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Les informations sont obligatoires."
            );
        }

        // ==========================================
        // 2. VERIFIER LE CODE ET VERROUILLER
        // ==========================================

        Ticket ticket = lockVerifiedAnonymousTicket(
                request.getReference(),
                request.getAccessCode()
        );

        // ==========================================
        // 3. VERIFIER LA DECISION
        // ==========================================

        if (request.getAction() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La décision est obligatoire."
            );
        }

        String action = request.getAction().toString();

        boolean accepted =
                "ACCEPTER_SOLUTION".equals(action);

        boolean refused =
                "REFUSER_SOLUTION".equals(action);

        if (!accepted && !refused) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Décision invalide."
            );
        }

        // ==========================================
        // 4. VERIFIER LE COMMENTAIRE
        // ==========================================

        String note = request.getNote() == null
                ? ""
                : request.getNote().trim();

        if (note.length() > 2000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le commentaire dépasse 2000 caractères."
            );
        }

        if (refused && note.length() < 10) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Expliquez pourquoi la solution est refusée."
            );
        }

        // ==========================================
        // 5. VERIFIER LE STATUT
        // ==========================================

        if (ticket.getStatus() != TicketStatus.ATTENTE_VALIDATION) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ce signalement n'attend pas de validation."
            );
        }

        if (ticket.getAssignedResponsable() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Aucun responsable n'est attribué au dossier."
            );
        }

        // ==========================================
        // 6. DETERMINER LE NOUVEAU STATUT
        // ==========================================

        TicketStatus oldStatus = ticket.getStatus();

        TicketStatus newStatus = accepted
                ? TicketStatus.RESOLU
                : TicketStatus.ROUVERT;

        String eventType = accepted
                ? "SOLUTION_ACCEPTEE"
                : "SOLUTION_REFUSEE";

        // ==========================================
        // 7. MODIFIER LE SIGNALEMENT
        // ==========================================

        ticket.setStatus(newStatus);

        // ==========================================
        // 8. CREER L'EVENEMENT D'HISTORIQUE
        // ==========================================

        TicketEvent event = new TicketEvent();

        event.setTicket(ticket);

        // Aucun compte utilisateur identifié.
        event.setActor(null);
        event.setActorKind("ANONYMOUS");

        event.setEventType(eventType);
        event.setOldStatus(oldStatus);
        event.setNewStatus(newStatus);
        event.setNote(
                note.isBlank() ? null : note
        );

        // ==========================================
        // 9. ENREGISTRER LES MODIFICATIONS
        // ==========================================

        ticketRepository.saveAndFlush(ticket);
        eventRepository.save(event);

        // ==========================================
        // 10. NOTIFIER LE RESPONSABLE EN CAS DE REFUS
        // ==========================================

        if (refused) {
            eventPublisher.publishEvent(
                    new TicketResponsibleNotificationEvent(
                            ticket.getId(),
                            TicketResponsibleNotificationEvent.Type
                                    .SOLUTION_REFUSEE
                    )
            );
        }

        // ==========================================
        // 11. RETOURNER LE RESULTAT
        // ==========================================

        return toActionResponse(ticket);
    }

    @Transactional
    public AnonymousTicketActionResponse closeAnonymousTicket(
            AnonymousTicketCloseRequest request
    ) {

        // ==========================================
        // 1. VERIFIER LA REQUETE
        // ==========================================

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Les informations sont obligatoires."
            );
        }

        // ==========================================
        // 2. VERIFIER LE CODE ET VERROUILLER
        // ==========================================

        Ticket ticket = lockVerifiedAnonymousTicket(
                request.getReference(),
                request.getAccessCode()
        );

        // ==========================================
        // 3. VERIFIER LE STATUT
        // ==========================================

        if (ticket.getStatus() != TicketStatus.RESOLU) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Seul un signalement résolu peut être clôturé."
            );
        }

        // ==========================================
        // 4. MODIFIER LE STATUT
        // ==========================================

        TicketStatus oldStatus = ticket.getStatus();

        ticket.setStatus(TicketStatus.CLOTURE);

        // ==========================================
        // 5. ENREGISTRER L'HISTORIQUE
        // ==========================================

        TicketEvent event = new TicketEvent();

        event.setTicket(ticket);
        event.setActor(null);
        event.setActorKind("ANONYMOUS");
        event.setEventType(
                "CLOTURE_SIGNALEMENT"
        );
        event.setOldStatus(oldStatus);
        event.setNewStatus(
                TicketStatus.CLOTURE
        );

        // ==========================================
        // 6. SAUVEGARDER
        // ==========================================

        ticketRepository.saveAndFlush(ticket);
        eventRepository.save(event);

        // ==========================================
        // 7. RETOURNER LE RESULTAT
        // ==========================================

        return toActionResponse(ticket);
    }

    private Ticket verifyAnonymousAccess(
            String referenceInput,
            String accessCodeInput
    ) {

        // 1. Nettoyer les informations reçues.

        String reference = referenceInput == null
                ? ""
                : referenceInput.trim();

        String accessCode = accessCodeInput == null
                ? ""
                : accessCodeInput.trim();

        // 2. Vérifier les formats.

        if (reference.isBlank()
                || reference.length() > 40
                || accessCode.isBlank()
                || accessCode.length() > 128) {

            throw invalidTrackingCredentials();
        }

        // 3. Rechercher uniquement un dossier anonyme.

        Ticket ticket = ticketRepository
                .findByReferenceAndAnonymousTrue(reference)
                .orElseThrow(
                        this::invalidTrackingCredentials
                );

        // 4. Récupérer l'empreinte enregistrée.

        String storedHash;

        try {
            storedHash = jdbcTemplate.queryForObject(
                    """
                    SELECT access_key_hash
                    FROM anonymous_ticket_access
                    WHERE ticket_id = ?
                    """,
                    String.class,
                    ticket.getId()
            );
        } catch (EmptyResultDataAccessException exception) {
            throw invalidTrackingCredentials();
        }

        if (storedHash == null) {
            throw invalidTrackingCredentials();
        }

        // 5. Calculer l'empreinte du code fourni.

        String providedHash = sha256(accessCode);

        // 6. Comparer les empreintes.

        byte[] expected = storedHash.trim()
                .getBytes(StandardCharsets.US_ASCII);

        byte[] provided = providedHash
                .getBytes(StandardCharsets.US_ASCII);

        if (!MessageDigest.isEqual(expected, provided)) {
            throw invalidTrackingCredentials();
        }

        // 7. Retourner le signalement autorisé.

        return ticket;
    }

    private Ticket lockVerifiedAnonymousTicket(
            String reference,
            String accessCode
    ) {

        // 1. Vérifier la référence et le secret.

        Ticket verifiedTicket = verifyAnonymousAccess(
                reference,
                accessCode
        );

        // 2. Verrouiller le signalement en base.

        Ticket ticket = ticketRepository
                .findByIdForUpdate(verifiedTicket.getId())
                .orElseThrow(
                        this::invalidTrackingCredentials
                );

        // 3. Vérifier qu'il reste anonyme.

        if (!ticket.isAnonymous()
                || ticket.getEmployee() != null) {

            throw invalidTrackingCredentials();
        }

        return ticket;
    }

    private AnonymousTicketActionResponse toActionResponse(
            Ticket ticket
    ) {
        AnonymousTicketActionResponse response =
                new AnonymousTicketActionResponse();

        response.setReference(
                ticket.getReference()
        );
        response.setStatus(
                ticket.getStatus().name()
        );

        return response;
    }

    private ResponseStatusException invalidTrackingCredentials() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Référence ou code de suivi invalide."
        );
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
