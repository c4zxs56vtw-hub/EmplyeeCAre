package com.employeecare.api.integration.ticket;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.department.repository.DepartmentRepository;
import com.employeecare.api.integration.config.PostgresIntegrationTestConfig;
import com.employeecare.api.responsable.entity.ResponsableAccount;
import com.employeecare.api.responsable.repository.ResponsableAccountRepository;
import com.employeecare.api.ticket.entity.Ticket;
import com.employeecare.api.ticket.entity.TicketCategory;
import com.employeecare.api.ticket.entity.TicketPriority;
import com.employeecare.api.ticket.entity.TicketStatus;
import com.employeecare.api.ticket.repository.TicketCategoryRepository;
import com.employeecare.api.ticket.repository.TicketRepository;
import com.employeecare.api.ticket.service.TicketService;
import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;
import com.employeecare.api.user.repository.AppUserRepository;
import com.employeecare.api.user.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresIntegrationTestConfig.class)
class TicketOwnershipIntegrationTest {

    private static final String CONTEXT_PATH = "/api";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private TicketCategoryRepository categoryRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ResponsableAccountRepository responsableAccountRepository;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Department department;
    private TicketCategory category;
    private Role employeRole;
    private Role responsableRole;

    @BeforeEach
    void setUp() {
        department = departmentRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    Department dep = new Department();
                    dep.setName("Direction Test " + UUID.randomUUID());
                    dep.setActive(true);
                    return departmentRepository.save(dep);
                });

        category = categoryRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    TicketCategory cat = new TicketCategory();
                    cat.setCode("TECHNIQUE");
                    cat.setLabel("Support Technique");
                    cat.setActive(true);
                    return categoryRepository.save(cat);
                });

        employeRole = roleRepository.findById("EMPLOYE").orElseThrow();
        responsableRole = roleRepository.findById("RESPONSABLE").orElseThrow();
    }

    private AppUser createActiveUser(String emailPrefix, Role role) {
        String email = emailPrefix + "-" + UUID.randomUUID() + "@employeecare.local";
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFirstName("Jean");
        user.setLastName("Dupont");
        user.setEmployeeNumber("EMP-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setStatus(AccountStatus.ACTIF);
        user.setDepartment(department);
        user.setRoles(Collections.singleton(role));
        return appUserRepository.save(user);
    }

    private String loginAndGetJwt(String email, String password) throws Exception {
        String loginBody = """
                {
                    "email": "%s",
                    "password": "%s"
                }
                """.formatted(email, password);

        String responseJson = mockMvc.perform(
                        post(CONTEXT_PATH + "/auth/login")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extraire la valeur de "token" du JSON
        int tokenIndex = responseJson.indexOf("\"token\":");
        int startIndex = responseJson.indexOf("\"", tokenIndex + 8) + 1;
        int endIndex = responseJson.indexOf("\"", startIndex);
        return responseJson.substring(startIndex, endIndex);
    }

    @Test
    @DisplayName("Scénario A : Un employé B ne peut pas consulter l'historique ou les PJ d'un employé A (404)")
    void employeeCannotAccessAnotherEmployeeTicket() throws Exception {
        // 1. Créer Employé A et Employé B
        AppUser employeA = createActiveUser("employe-a", employeRole);
        AppUser employeB = createActiveUser("employe-b", employeRole);

        // 2. Créer un signalement appartenant à Employé A
        Ticket ticket = new Ticket();
        ticket.setReference("TICK-" + UUID.randomUUID().toString().substring(0, 10));
        ticket.setTitle("Problème matériel poste A");
        ticket.setDescription("Mon écran scintille depuis ce matin.");
        ticket.setEmployee(employeA);
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setPriority(TicketPriority.MOYENNE);
        ticket.setStatus(TicketStatus.EN_ATTENTE);
        ticket.setConfidential(false);
        ticket = ticketRepository.save(ticket);
        UUID ticketId = ticket.getId();

        // 3. Authentification réelle d'Employé B (obtention d'un vrai JWT)
        String jwtEmployeB = loginAndGetJwt(employeB.getEmail(), "Password123!");

        // 4. Employé B tente d'accéder à l'historique du signalement de A -> 404 Not Found
        mockMvc.perform(get(CONTEXT_PATH + "/employe/tickets/" + ticketId + "/history")
                        .contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtEmployeB))
                .andExpect(status().isNotFound());

        // 5. Employé B tente d'accéder aux pièces jointes du signalement de A -> 404 Not Found
        mockMvc.perform(get(CONTEXT_PATH + "/employe/tickets/" + ticketId + "/attachments")
                        .contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtEmployeB))
                .andExpect(status().isNotFound());

        // 6. Employé A (le propriétaire) peut accéder à son historique -> 200 OK
        String jwtEmployeA = loginAndGetJwt(employeA.getEmail(), "Password123!");
        mockMvc.perform(get(CONTEXT_PATH + "/employe/tickets/" + ticketId + "/history")
                        .contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtEmployeA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Scénario E : Deux prises en charge concurrentes -> exactement 1 succès (200) et 1 conflit (409)")
    void concurrentTakeChargeShouldAllowOnlyOneWinner() throws Exception {
        // 1. Créer deux responsables avec profil ResponsableAccount actif
        AppUser userRespA = createActiveUser("resp-a", responsableRole);
        ResponsableAccount respA = new ResponsableAccount();
        respA.setUser(userRespA);
        respA.setDepartment(department);
        respA.setActive(true);
        respA.setConfidentialHabilitation(false);
        respA.setAssignedCategories(Set.of(category.getCode()));
        responsableAccountRepository.save(respA);

        AppUser userRespB = createActiveUser("resp-b", responsableRole);
        ResponsableAccount respB = new ResponsableAccount();
        respB.setUser(userRespB);
        respB.setDepartment(department);
        respB.setActive(true);
        respB.setConfidentialHabilitation(false);
        respB.setAssignedCategories(Set.of(category.getCode()));
        responsableAccountRepository.save(respB);

        // 2. Créer un signalement EN_ATTENTE
        AppUser employe = createActiveUser("emp-creator", employeRole);
        Ticket ticket = new Ticket();
        ticket.setReference("TICK-CONC-" + UUID.randomUUID().toString().substring(0, 8));
        ticket.setTitle("Signalement pour test de concurrence");
        ticket.setDescription("Test prise en charge simultanée");
        ticket.setEmployee(employe);
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setPriority(TicketPriority.HAUTE);
        ticket.setStatus(TicketStatus.EN_ATTENTE);
        ticket.setConfidential(false);
        ticket = ticketRepository.save(ticket);
        UUID ticketId = ticket.getId();

        // 3. Déclencher 2 prises en charge concurrentes
        CompletableFuture<Integer> futureA = CompletableFuture.supplyAsync(() -> {
            try {
                ticketService.takeCharge(userRespA.getId(), ticketId);
                return 200;
            } catch (ResponseStatusException rse) {
                return rse.getStatusCode().value();
            }
        });

        CompletableFuture<Integer> futureB = CompletableFuture.supplyAsync(() -> {
            try {
                ticketService.takeCharge(userRespB.getId(), ticketId);
                return 200;
            } catch (ResponseStatusException rse) {
                return rse.getStatusCode().value();
            }
        });

        int statusA = futureA.get();
        int statusB = futureB.get();

        // Exactement l'une des opérations doit être 200, l'autre doit être 409 Conflict
        List<Integer> statuses = List.of(statusA, statusB);
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        // 4. Vérifier en base : statut EN_COURS et un seul responsable attribué
        Ticket reloaded = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.DIAGNOSTIC_EN_COURS);
        assertThat(reloaded.getAssignedResponsable()).isNotNull();
        assertThat(List.of(userRespA.getId(), userRespB.getId()))
                .contains(reloaded.getAssignedResponsable().getId());
    }
}
