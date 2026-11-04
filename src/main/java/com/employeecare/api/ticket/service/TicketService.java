package com.employeecare.api.ticket.service;

import com.employeecare.api.generated.models.CreateTicketRequest;
import com.employeecare.api.generated.models.TicketSummary;

import com.employeecare.api.ticket.entity.Ticket;
import com.employeecare.api.ticket.entity.TicketCategory;
import com.employeecare.api.ticket.entity.TicketPriority;
import com.employeecare.api.ticket.entity.TicketStatus;

import com.employeecare.api.ticket.repository.TicketCategoryRepository;
import com.employeecare.api.ticket.repository.TicketRepository;

import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.repository.AppUserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository categoryRepository;
    private final AppUserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public TicketService(
            TicketRepository ticketRepository,
            TicketCategoryRepository categoryRepository,
            AppUserRepository userRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
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
