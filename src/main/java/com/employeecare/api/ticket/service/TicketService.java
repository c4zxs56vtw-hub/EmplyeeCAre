package com.employeecare.api.ticket.service;

import com.employeecare.api.generated.models.CreateTicketRequest;
import com.employeecare.api.generated.models.TicketSummary;

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

    public TicketService(

            TicketRepository ticketRepository,

            TicketCategoryRepository categoryRepository,

            AppUserRepository userRepository,

            JdbcTemplate jdbcTemplate,

            ResponsableAccountRepository responsableAccountRepository,

            TicketEventRepository ticketEventRepository

    ) {

        this.ticketRepository = ticketRepository;

        this.categoryRepository = categoryRepository;

        this.userRepository = userRepository;

        this.jdbcTemplate = jdbcTemplate;

        this.responsableAccountRepository = responsableAccountRepository;

        this.ticketEventRepository = ticketEventRepository;
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

        Ticket savedTicket = ticketRepository.saveAndFlush(ticket);

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


        // =====================================
        // 8. RETOURNER LA REPONSE
        // =====================================

        return toTicketSummary(ticket);
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
