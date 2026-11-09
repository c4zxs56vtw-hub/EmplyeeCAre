package com.employeecare.api.ticket.service;

import com.employeecare.api.generated.models.CreateTicketRequest;
import com.employeecare.api.generated.models.ReviewTicketRequest;
import com.employeecare.api.generated.models.TicketHistoryEvent;
import com.employeecare.api.generated.models.TicketSummary;
import com.employeecare.api.generated.models.UpdateTicketStatusRequest;

import com.employeecare.api.notification.event.TicketEmailNotificationEvent;
import com.employeecare.api.notification.event.TicketResponsibleNotificationEvent;

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
import com.employeecare.api.user.repository.AppUserRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository categoryRepository;
    private final AppUserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ResponsableAccountRepository responsableAccountRepository;
    private final TicketEventRepository ticketEventRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TicketService(

            TicketRepository ticketRepository,

            TicketCategoryRepository categoryRepository,

            AppUserRepository userRepository,

            JdbcTemplate jdbcTemplate,

            ResponsableAccountRepository responsableAccountRepository,

            TicketEventRepository ticketEventRepository,

            ApplicationEventPublisher eventPublisher

    ) {

        this.ticketRepository = ticketRepository;

        this.categoryRepository = categoryRepository;

        this.userRepository = userRepository;

        this.jdbcTemplate = jdbcTemplate;

        this.responsableAccountRepository = responsableAccountRepository;

        this.ticketEventRepository = ticketEventRepository;

        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TicketSummary createTicket(
            UUID currentUserId,
            CreateTicketRequest request
    ) {

        // 1. Récupérer l'employé connecté

        AppUser employee = userRepository
                .findByIdWithRoles(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Utilisateur introuvable."
                ));

        // 2. Vérifier son statut

        if (employee.getStatus() != AccountStatus.ACTIF) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le compte n'est pas actif."
            );
        }

        // 3. Vérifier le rôle EMPLOYE

        boolean isEmployee = employee.getRoles()
                .stream()
                .anyMatch(role ->
                        "EMPLOYE".equals(role.getCode())
                );

        if (!isEmployee) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le rôle EMPLOYE est requis."
            );
        }

        // 4. Vérifier le département

        if (employee.getDepartment() == null
                || !employee.getDepartment().isActive()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Aucun département actif associé à cet employé."
            );
        }

        // 5. Vérifier les informations reçues

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
                    "Le titre doit contenir entre 5 et 200 caractères."
            );
        }

        if (description.length() < 10
                || description.length() > 5000) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La description doit contenir entre 10 et 5000 caractères."
            );
        }

        // 6. Vérifier la catégorie

        String categoryCode = request.getCategoryCode() == null
                ? ""
                : request.getCategoryCode()
                        .trim()
                        .toUpperCase(Locale.ROOT);

        if (categoryCode.isEmpty() || categoryCode.length() > 100) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Code de catégorie invalide."
            );
        }

        TicketCategory category = categoryRepository
                .findById(categoryCode)
                .filter(TicketCategory::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Catégorie inexistante ou désactivée."
                ));

        // 7. Générer une référence unique

        Long sequenceNumber = jdbcTemplate.queryForObject(
                "SELECT nextval('ticket_reference_seq')",
                Long.class
        );

        int year = Year.now(
                ZoneId.of("Africa/Douala")
        ).getValue();

        String reference = String.format(
                "SIG-%d-%06d",
                year,
                sequenceNumber
        );

        // 8. Construire le signalement

        Ticket ticket = new Ticket();

        ticket.setReference(reference);

        ticket.setTitle(title);

        ticket.setDescription(description);

        ticket.setEmployee(employee);

        ticket.setDepartment(employee.getDepartment());

        ticket.setCategory(category);

        ticket.setPriority(TicketPriority.MOYENNE);

        ticket.setStatus(TicketStatus.EN_ATTENTE);

        ticket.setConfidential(
                Boolean.TRUE.equals(request.getIsConfidential())
        );

        // 9. Enregistrer dans PostgreSQL

        // 1. Enregistrer le signalement.

        Ticket savedTicket =
                ticketRepository.saveAndFlush(ticket);


        // 2. Créer l'événement de création.

        TicketEvent event = new TicketEvent();

        event.setTicket(savedTicket);

        event.setActor(employee);

        event.setEventType(
                "CREATION_SIGNALEMENT"
        );

        event.setOldStatus(null);

        event.setNewStatus(
                TicketStatus.EN_ATTENTE
        );


        // 3. Enregistrer l'événement.

        ticketEventRepository.save(event);


        // Notifier les responsables habilités.

        eventPublisher.publishEvent(

                new TicketResponsibleNotificationEvent(

                        savedTicket.getId(),

                        TicketResponsibleNotificationEvent.Type.NOUVEAU_SIGNALEMENT
                )
        );


        // 4. Retourner la réponse.

        return toTicketSummary(savedTicket);
    }

    @Transactional(readOnly = true)
    public List<TicketSummary> getMyTickets(UUID employeeId) {

        // 1. Récupérer les signalements de l'employé

        List<Ticket> tickets = ticketRepository
                .findByEmployee_IdOrderByCreatedAtDesc(
                        employeeId
                );

        // 2. Transformer les entités en réponses API

        return tickets.stream()
                .map(this::toTicketSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketSummary> getResponsableTickets(
            UUID responsableId
    ) {

        // 1. Rechercher le profil responsable

        ResponsableAccount responsable =
                responsableAccountRepository
                        .findById(responsableId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Aucun profil responsable associé à ce compte."
                        ));

        // 2. Vérifier que le profil est actif

        if (!responsable.isActive()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le profil responsable est désactivé."
            );
        }

        // 3. Vérifier le département attribué

        if (responsable.getDepartment() == null) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Aucun département attribué au responsable."
            );
        }

        UUID departmentId =
                responsable.getDepartment().getId();

        // 4. Récupérer les catégories attribuées

        Set<String> categoryCodes =
                responsable.getAssignedCategories();

        // 5. Aucune catégorie = aucun signalement visible

        if (categoryCodes == null || categoryCodes.isEmpty()) {

            return List.of();
        }

        // 6. Vérifier l'habilitation confidentielle

        boolean canReadConfidential =
                responsable.isConfidentialHabilitation();

        // 7. Récupérer uniquement les signalements autorisés

        List<Ticket> tickets = ticketRepository
                .findVisibleForResponsable(
                        departmentId,
                        categoryCodes,
                        canReadConfidential
                );

        // 8. Construire la réponse

        return tickets.stream()
                .map(this::toTicketSummary)
                .toList();
    }

    @Transactional
    public TicketSummary takeCharge(
            UUID responsableId,
            UUID ticketId
    ) {

        // =====================================
        // 1. VERIFIER LE PROFIL RESPONSABLE
        // =====================================

        ResponsableAccount responsable =
                responsableAccountRepository
                        .findById(responsableId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Profil responsable introuvable."
                        ));

        if (!responsable.isActive()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le profil responsable est désactivé."
            );
        }

        AppUser actor = responsable.getUser();

        // Vérifier que le compte est actif.

        if (actor.getStatus() != AccountStatus.ACTIF) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le compte utilisateur est désactivé."
            );
        }

        // Vérifier que le rôle RESPONSABLE existe toujours.

        boolean hasResponsableRole = actor.getRoles()
                .stream()
                .anyMatch(role ->
                        "RESPONSABLE".equals(role.getCode())
                );

        if (!hasResponsableRole) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le rôle RESPONSABLE est requis."
            );
        }


        // =====================================
        // 2. RECUPERER ET VERROUILLER LE TICKET
        // =====================================

        Ticket ticket = ticketRepository
                .findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Signalement introuvable."
                ));


        // =====================================
        // 3. VERIFIER LE PERIMETRE
        // =====================================

        boolean sameDepartment =
                responsable.getDepartment() != null
                && responsable.getDepartment().getId()
                        .equals(ticket.getDepartment().getId());

        boolean allowedCategory =
                responsable.getAssignedCategories()
                        .contains(ticket.getCategory().getCode());

        boolean confidentialAllowed =
                !ticket.isConfidential()
                || responsable.isConfidentialHabilitation();

        if (!sameDepartment
                || !allowedCategory
                || !confidentialAllowed) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Signalement introuvable."
            );
        }


        // =====================================
        // 4. VERIFIER LA DISPONIBILITE
        // =====================================

        if (ticket.getAssignedResponsable() != null
                || ticket.getStatus() != TicketStatus.EN_ATTENTE) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ce signalement ne peut plus être pris en charge."
            );
        }


        // =====================================
        // 5. ATTRIBUER LE RESPONSABLE
        // =====================================

        ticket.setAssignedResponsable(actor);

        ticket.setTakenAt(Instant.now());

        ticket.setStatus(
                TicketStatus.DIAGNOSTIC_EN_COURS
        );


        // =====================================
        // 6. ENREGISTRER L'HISTORIQUE
        // =====================================

        TicketEvent event = new TicketEvent();

        event.setTicket(ticket);

        event.setActor(actor);

        event.setEventType("PRISE_EN_CHARGE");

        event.setOldStatus(
                TicketStatus.EN_ATTENTE
        );

        event.setNewStatus(
                TicketStatus.DIAGNOSTIC_EN_COURS
        );


        // =====================================
        // 7. SAUVEGARDER
        // =====================================

        ticketRepository.saveAndFlush(ticket);

        ticketEventRepository.save(event);

        // Publier une notification après la prise en charge.

        eventPublisher.publishEvent(
                new TicketEmailNotificationEvent(
                        ticket.getEmployee().getEmail(),
                        TicketEmailNotificationEvent.Type.PRISE_EN_CHARGE
                )
        );


        // =====================================
        // 8. RETOURNER LA REPONSE
        // =====================================

        return toTicketSummary(ticket);
    }

    @Transactional
    public TicketSummary submitForValidation(
            UUID responsableId,
            UUID ticketId,
            UpdateTicketStatusRequest request
    ) {

        // ======================================
        // 1. VALIDER LA REQUETE
        // ======================================

        if (request == null
                || request.getStatus() == null
                || !"ATTENTE_VALIDATION".equals(
                        request.getStatus().toString()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le statut demandé est invalide."
            );
        }

        String note = request.getNote() == null
                ? ""
                : request.getNote().trim();

        if (note.length() < 10 || note.length() > 2000) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La note doit contenir entre 10 et 2000 caractères."
            );
        }


        // ======================================
        // 2. VERIFIER LE RESPONSABLE
        // ======================================

        ResponsableAccount responsable =
                responsableAccountRepository
                        .findById(responsableId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Profil responsable introuvable."
                        ));

        if (!responsable.isActive()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le profil responsable est désactivé."
            );
        }

        AppUser actor = responsable.getUser();

        if (actor.getStatus() != AccountStatus.ACTIF) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le compte responsable n'est pas actif."
            );
        }

        boolean hasResponsableRole = actor.getRoles()
                .stream()
                .anyMatch(role ->
                        "RESPONSABLE".equals(role.getCode())
                );

        if (!hasResponsableRole) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le rôle RESPONSABLE est requis."
            );
        }


        // ======================================
        // 3. VERROUILLER LE SIGNALEMENT
        // ======================================

        Ticket ticket = ticketRepository
                .findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Signalement introuvable."
                ));


        // ======================================
        // 4. VERIFIER LE RESPONSABLE ATTRIBUE
        // ======================================

        if (ticket.getAssignedResponsable() == null
                || !ticket.getAssignedResponsable()
                        .getId()
                        .equals(responsableId)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Signalement introuvable."
            );
        }


        // ======================================
        // 5. VERIFIER LE PERIMETRE ACTUEL
        // ======================================

        boolean sameDepartment =
                responsable.getDepartment() != null
                && responsable.getDepartment()
                        .getId()
                        .equals(ticket.getDepartment().getId());

        boolean allowedCategory =
                responsable.getAssignedCategories()
                        .contains(ticket.getCategory().getCode());

        boolean confidentialAllowed =
                !ticket.isConfidential()
                || responsable.isConfidentialHabilitation();

        if (!sameDepartment
                || !allowedCategory
                || !confidentialAllowed) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Signalement introuvable."
            );
        }


        // ======================================
        // 6. VERIFIER LA TRANSITION
        // ======================================

        if (ticket.getStatus()
                != TicketStatus.DIAGNOSTIC_EN_COURS) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ce signalement ne peut pas être soumis à validation."
            );
        }


        // ======================================
        // 7. MODIFIER LE STATUT
        // ======================================

        TicketStatus oldStatus = ticket.getStatus();

        ticket.setStatus(
                TicketStatus.ATTENTE_VALIDATION
        );


        // ======================================
        // 8. ENREGISTRER L'HISTORIQUE
        // ======================================

        TicketEvent event = new TicketEvent();

        event.setTicket(ticket);

        event.setActor(actor);

        event.setEventType(
                "SOUMISSION_VALIDATION"
        );

        event.setOldStatus(oldStatus);

        event.setNewStatus(
                TicketStatus.ATTENTE_VALIDATION
        );

        event.setNote(note);


        // ======================================
        // 9. SAUVEGARDER
        // ======================================

        ticketRepository.saveAndFlush(ticket);

        ticketEventRepository.save(event);

        // Notifier l'employé qu'une solution attend sa validation.

        eventPublisher.publishEvent(
                new TicketEmailNotificationEvent(
                        ticket.getEmployee().getEmail(),
                        TicketEmailNotificationEvent.Type.SOLUTION_PROPOSEE
                )
        );


        // ======================================
        // 10. RETOURNER LE RESULTAT
        // ======================================

        return toTicketSummary(ticket);
    }

    @Transactional
    public TicketSummary reviewTicket(

            UUID employeeId,

            UUID ticketId,

            ReviewTicketRequest request

    ) {

        // =========================================
        // 1. VERIFIER LA REQUETE
        // =========================================

        if (request == null || request.getAction() == null) {

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
                    "Décision non reconnue."
            );
        }


        // =========================================
        // 2. VERIFIER LE COMMENTAIRE
        // =========================================

        String note = request.getNote() == null
                ? ""
                : request.getNote().trim();

        if (note.length() > 2000) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le commentaire ne doit pas dépasser 2000 caractères."
            );
        }

        if (refused && note.length() < 10) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Expliquez pourquoi la solution ne fonctionne pas (10 caractères minimum)."
            );
        }


        // =========================================
        // 3. VERIFIER L'EMPLOYE CONNECTE
        // =========================================

        AppUser employee = userRepository
                .findByIdWithRoles(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Utilisateur introuvable."
                ));

        if (employee.getStatus() != AccountStatus.ACTIF) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le compte employé est désactivé."
            );
        }

        boolean hasEmployeeRole = employee.getRoles()
                .stream()
                .anyMatch(role ->
                        "EMPLOYE".equals(role.getCode())
                );

        if (!hasEmployeeRole) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le rôle EMPLOYE est requis."
            );
        }


        // =========================================
        // 4. RECUPERER SON PROPRE SIGNALEMENT
        // =========================================

        Ticket ticket = ticketRepository
                .findOwnedByIdForUpdate(
                        ticketId,
                        employeeId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Signalement introuvable."
                ));


        // =========================================
        // 5. VERIFIER LE STATUT ACTUEL
        // =========================================

        if (ticket.getStatus()
                != TicketStatus.ATTENTE_VALIDATION) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ce signalement n'est pas en attente de validation."
            );
        }

        if (ticket.getAssignedResponsable() == null) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Aucun responsable n'est attribué à ce signalement."
            );
        }


        // =========================================
        // 6. DETERMINER LE NOUVEAU STATUT
        // =========================================

        TicketStatus oldStatus = ticket.getStatus();

        TicketStatus newStatus;

        String eventType;

        if (accepted) {

            newStatus = TicketStatus.RESOLU;

            eventType = "SOLUTION_ACCEPTEE";

        } else {

            newStatus = TicketStatus.ROUVERT;

            eventType = "SOLUTION_REFUSEE";
        }


        // =========================================
        // 7. MODIFIER LE SIGNALEMENT
        // =========================================

        ticket.setStatus(newStatus);


        // =========================================
        // 8. ENREGISTRER L'HISTORIQUE
        // =========================================

        TicketEvent event = new TicketEvent();

        event.setTicket(ticket);

        event.setActor(employee);

        event.setEventType(eventType);

        event.setOldStatus(oldStatus);

        event.setNewStatus(newStatus);

        event.setNote(
                note.isEmpty() ? null : note
        );


        // =========================================
        // 9. ENREGISTRER DANS POSTGRESQL
        // =========================================

        ticketRepository.saveAndFlush(ticket);

        ticketEventRepository.save(event);


        // Notifier le responsable si la solution est refusée.

        if (refused) {

            eventPublisher.publishEvent(

                    new TicketResponsibleNotificationEvent(

                            ticket.getId(),

                            TicketResponsibleNotificationEvent.Type.SOLUTION_REFUSEE
                    )
            );
        }


        // =========================================
        // 10. RETOURNER LA REPONSE
        // =========================================

        return toTicketSummary(ticket);
    }

    @Transactional
    public TicketSummary resumeTicket(

            UUID responsableId,

            UUID ticketId

    ) {

        // =========================================
        // 1. VERIFIER LE COMPTE RESPONSABLE
        // =========================================

        ResponsableAccount responsableAccount =
                responsableAccountRepository
                        .findById(responsableId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Aucun périmètre responsable trouvé."
                        ));

        if (!responsableAccount.isActive()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le périmètre responsable est désactivé."
            );
        }


        // =========================================
        // 2. VERIFIER LE COMPTE UTILISATEUR
        // =========================================

        AppUser actor = userRepository
                .findByIdWithRoles(responsableId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Utilisateur introuvable."
                ));

        if (actor.getStatus() != AccountStatus.ACTIF) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le compte responsable est désactivé."
            );
        }

        boolean hasResponsableRole = actor.getRoles()
                .stream()
                .anyMatch(role ->
                        "RESPONSABLE".equals(role.getCode())
                );

        if (!hasResponsableRole) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le rôle RESPONSABLE est requis."
            );
        }


        // =========================================
        // 3. RECUPERER ET VERROUILLER LE SIGNALEMENT
        // =========================================

        Ticket ticket = ticketRepository
                .findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Signalement introuvable."
                ));


        // =========================================
        // 4. VERIFIER LE RESPONSABLE ATTRIBUE
        // =========================================

        if (ticket.getAssignedResponsable() == null) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Signalement introuvable."
            );
        }

        boolean isAssignedResponsable =
                ticket.getAssignedResponsable()
                        .getId()
                        .equals(responsableId);

        if (!isAssignedResponsable) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Signalement introuvable."
            );
        }


        // =========================================
        // 5. VERIFIER LE PERIMETRE DU RESPONSABLE
        // =========================================

        boolean sameDepartment =
                responsableAccount.getDepartment() != null
                && responsableAccount.getDepartment()
                        .getId()
                        .equals(ticket.getDepartment().getId());

        if (!sameDepartment) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Signalement introuvable."
            );
        }

        boolean categoryAllowed =
                responsableAccount
                        .getAssignedCategories()
                        .contains(ticket.getCategory().getCode());

        if (!categoryAllowed) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Signalement introuvable."
            );
        }

        if (ticket.isConfidential()
                && !responsableAccount.isConfidentialHabilitation()) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Signalement introuvable."
            );
        }


        // =========================================
        // 6. VERIFIER LE STATUT ACTUEL
        // =========================================

        if (ticket.getStatus() != TicketStatus.ROUVERT) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Seul un signalement rouvert peut être repris."
            );
        }


        // =========================================
        // 7. METTRE A JOUR LE STATUT
        // =========================================

        TicketStatus oldStatus = ticket.getStatus();

        ticket.setStatus(
                TicketStatus.DIAGNOSTIC_EN_COURS
        );


        // =========================================
        // 8. ENREGISTRER L'EVENEMENT
        // =========================================

        TicketEvent event = new TicketEvent();

        event.setTicket(ticket);

        event.setActor(actor);

        event.setEventType(
                "REPRISE_TRAITEMENT"
        );

        event.setOldStatus(oldStatus);

        event.setNewStatus(
                TicketStatus.DIAGNOSTIC_EN_COURS
        );


        // =========================================
        // 9. SAUVEGARDER EN BASE
        // =========================================

        ticketRepository.saveAndFlush(ticket);

        ticketEventRepository.save(event);


        // =========================================
        // 10. RETOURNER LE RESULTAT
        // =========================================

        return toTicketSummary(ticket);
    }

    @Transactional
    public TicketSummary closeTicket(
            UUID employeeId,
            UUID ticketId
    ) {

        // ==========================================
        // 1. VERIFIER LE COMPTE EMPLOYE
        // ==========================================

        AppUser employee = userRepository
                .findByIdWithRoles(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Utilisateur introuvable."
                ));

        if (employee.getStatus() != AccountStatus.ACTIF) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le compte employé est désactivé."
            );
        }


        // ==========================================
        // 2. VERIFIER LE ROLE EMPLOYE
        // ==========================================

        boolean hasEmployeeRole = employee.getRoles()
                .stream()
                .anyMatch(role ->
                        "EMPLOYE".equals(role.getCode())
                );

        if (!hasEmployeeRole) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Le rôle EMPLOYE est requis."
            );
        }


        // ==========================================
        // 3. RECUPERER LE SIGNALEMENT
        // ==========================================

        Ticket ticket = ticketRepository
                .findOwnedByIdForUpdate(
                        ticketId,
                        employeeId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Signalement introuvable."
                ));


        // ==========================================
        // 4. VERIFIER LE STATUT ACTUEL
        // ==========================================

        if (ticket.getStatus() != TicketStatus.RESOLU) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Seul un signalement résolu peut être clôturé."
            );
        }


        // ==========================================
        // 5. MODIFIER LE STATUT
        // ==========================================

        TicketStatus oldStatus = ticket.getStatus();

        ticket.setStatus(
                TicketStatus.CLOTURE
        );


        // ==========================================
        // 6. CREER L'EVENEMENT D'HISTORIQUE
        // ==========================================

        TicketEvent event = new TicketEvent();

        event.setTicket(ticket);

        event.setActor(employee);

        event.setEventType(
                "CLOTURE_SIGNALEMENT"
        );

        event.setOldStatus(oldStatus);

        event.setNewStatus(
                TicketStatus.CLOTURE
        );


        // ==========================================
        // 7. ENREGISTRER LES MODIFICATIONS
        // ==========================================

        ticketRepository.saveAndFlush(ticket);

        ticketEventRepository.save(event);


        // ==========================================
        // 8. CONSTRUIRE LA REPONSE
        // ==========================================

        return toTicketSummary(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketHistoryEvent> getMyTicketHistory(
            UUID employeeId,
            UUID ticketId
    ) {

        // 1. Vérifier que le signalement appartient à l'employé.

        boolean isOwner = ticketRepository
                .existsByIdAndEmployee_Id(
                        ticketId,
                        employeeId
                );

        if (!isOwner) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Signalement introuvable."
            );
        }

        // 2. Récupérer l'historique.

        return loadTicketHistory(ticketId);
    }

    @Transactional(readOnly = true)
    public List<TicketHistoryEvent> getResponsableTicketHistory(
            UUID responsableId,
            UUID ticketId
    ) {

        // 1. Vérifier le profil responsable.

        ResponsableAccount responsable =
                responsableAccountRepository
                        .findById(responsableId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Signalement introuvable."
                        ));

        if (!responsable.isActive()) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Signalement introuvable."
            );
        }

        // 2. Récupérer le signalement.

        Ticket ticket = ticketRepository
                .findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Signalement introuvable."
                ));

        // 3. Vérifier que le signalement lui est attribué.

        boolean isAssigned =
                ticket.getAssignedResponsable() != null
                && ticket.getAssignedResponsable()
                        .getId()
                        .equals(responsableId);

        // 4. Vérifier le département.

        boolean sameDepartment =
                responsable.getDepartment() != null
                && responsable.getDepartment()
                        .getId()
                        .equals(ticket.getDepartment().getId());

        // 5. Vérifier la catégorie.

        boolean allowedCategory =
                responsable.getAssignedCategories()
                        .contains(ticket.getCategory().getCode());

        // 6. Vérifier la confidentialité.

        boolean confidentialAllowed =
                !ticket.isConfidential()
                || responsable.isConfidentialHabilitation();

        // 7. Refuser tout accès non autorisé.

        if (!isAssigned
                || !sameDepartment
                || !allowedCategory
                || !confidentialAllowed) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Signalement introuvable."
            );
        }

        // 8. Récupérer l'historique.

        return loadTicketHistory(ticketId);
    }

    private List<TicketHistoryEvent> loadTicketHistory(
            UUID ticketId
    ) {

        List<TicketEvent> events =
                ticketEventRepository
                        .findHistoryByTicketId(ticketId);

        return events.stream()
                .map(this::toHistoryEvent)
                .toList();
    }

    private TicketHistoryEvent toHistoryEvent(
            TicketEvent event
    ) {

        TicketHistoryEvent response =
                new TicketHistoryEvent();

        response.setId(
                event.getId().toString()
        );

        response.setEventType(
                event.getEventType()
        );

        // Ne pas exposer directement les données personnelles.

        response.setActorLabel(
                getActorLabel(event.getEventType())
        );

        if (event.getOldStatus() != null) {

            response.setPreviousStatus(
                    event.getOldStatus().name()
            );
        }

        if (event.getNewStatus() != null) {

            response.setNewStatus(
                    event.getNewStatus().name()
            );
        }

        response.setNote(
                event.getNote()
        );

        response.setOccurredAt(
                event.getCreatedAt().toString()
        );

        return response;
    }

    private String getActorLabel(String eventType) {

        return switch (eventType) {

            case "PRISE_EN_CHARGE",
                 "SOUMISSION_VALIDATION",
                 "REPRISE_TRAITEMENT" -> "Responsable";

            case "CREATION_SIGNALEMENT",
                 "SOLUTION_ACCEPTEE",
                 "SOLUTION_REFUSEE",
                 "CLOTURE_SIGNALEMENT" -> "Employé";

            default -> "Intervenant";
        };
    }

    private TicketSummary toTicketSummary(Ticket ticket) {

        TicketSummary response = new TicketSummary();

        response.setId(
                ticket.getId().toString()
        );

        response.setReference(
                ticket.getReference()
        );

        response.setTitle(
                ticket.getTitle()
        );

        response.setCategory(
                ticket.getCategory().getLabel()
        );

        response.setPriority(
                TicketSummary.PriorityEnum.fromValue(
                        ticket.getPriority().name()
                )
        );

        response.setStatus(
                TicketSummary.StatusEnum.fromValue(
                        ticket.getStatus().name()
                )
        );

        response.setIsConfidential(
                ticket.isConfidential()
        );

        DateTimeFormatter formatter = DateTimeFormatter
                .ofPattern("dd/MM/uuuu")
                .withZone(ZoneId.of("Africa/Douala"));

        response.setCreatedAt(
                formatter.format(ticket.getCreatedAt())
        );

        return response;
    }
}
