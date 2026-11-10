package com.employeecare.api.integration.responsable;

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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

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
class ResponsableAccessIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    private Department department;
    private TicketCategory category;
    private Role responsableRole;

    @BeforeEach
    void setUp() {
        department = departmentRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    Department dep = new Department();
                    dep.setName("Direction RH " + UUID.randomUUID());
                    dep.setActive(true);
                    return departmentRepository.save(dep);
                });

        category = categoryRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    TicketCategory cat = new TicketCategory();
                    cat.setCode("RH");
                    cat.setLabel("Ressources Humaines");
                    cat.setActive(true);
                    return categoryRepository.save(cat);
                });

        responsableRole = roleRepository.findById("RESPONSABLE").orElseThrow();
    }

    private AppUser createResponsableUser(String emailPrefix) {
        String email = emailPrefix + "-" + UUID.randomUUID() + "@employeecare.local";
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFirstName("Claire");
        user.setLastName("Martin");
        user.setEmployeeNumber("RESP-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setStatus(AccountStatus.ACTIF);
        user.setDepartment(department);
        user.setRoles(Collections.singleton(responsableRole));
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

        int tokenIndex = responseJson.indexOf("\"token\":");
        int startIndex = responseJson.indexOf("\"", tokenIndex + 8) + 1;
        int endIndex = responseJson.indexOf("\"", startIndex);
        return responseJson.substring(startIndex, endIndex);
    }

    @Test
    @DisplayName("Scénario B : Responsable sans habilitation confidentielle refusé (404), et révocation immédiate en base")
    void nonHabilitatedResponsableMustBeRefused() throws Exception {
        // 1. Créer Responsable A (habilité confidentiel) et Responsable B (non habilité)
        AppUser userA = createResponsableUser("resp-habilite");
        ResponsableAccount accountA = new ResponsableAccount();
        accountA.setUser(userA);
        accountA.setDepartment(department);
        accountA.setActive(true);
        accountA.setConfidentialHabilitation(true);
        accountA.setAssignedCategories(Set.of(category.getCode()));
        responsableAccountRepository.save(accountA);

        AppUser userB = createResponsableUser("resp-non-habilite");
        ResponsableAccount accountB = new ResponsableAccount();
        accountB.setUser(userB);
        accountB.setDepartment(department);
        accountB.setActive(true);
        accountB.setConfidentialHabilitation(false);
        accountB.setAssignedCategories(Set.of(category.getCode()));
        responsableAccountRepository.save(accountB);

        // 2. Créer un employé créateur et un signalement confidentiel attribué à Responsable A
        Role empRole = roleRepository.findById("EMPLOYE").orElseThrow();
        AppUser author = new AppUser();
        author.setEmail("emp-author-" + UUID.randomUUID() + "@employeecare.local");
        author.setFirstName("Paul");
        author.setLastName("Auteur");
        author.setEmployeeNumber("EMP-" + UUID.randomUUID().toString().substring(0, 8));
        author.setPasswordHash(passwordEncoder.encode("Password123!"));
        author.setStatus(AccountStatus.ACTIF);
        author.setDepartment(department);
        author.setRoles(Collections.singleton(empRole));
        author = appUserRepository.save(author);

        Ticket confidentialTicket = new Ticket();
        confidentialTicket.setReference("CONF-" + UUID.randomUUID().toString().substring(0, 8));
        confidentialTicket.setTitle("Harcèlement présumé");
        confidentialTicket.setDescription("Signalement sous haute confidentialité.");
        confidentialTicket.setEmployee(author);
        confidentialTicket.setDepartment(department);
        confidentialTicket.setCategory(category);
        confidentialTicket.setPriority(TicketPriority.HAUTE);
        confidentialTicket.setStatus(TicketStatus.DIAGNOSTIC_EN_COURS);
        confidentialTicket.setConfidential(true);
        confidentialTicket.setAssignedResponsable(userA);
        confidentialTicket = ticketRepository.save(confidentialTicket);
        UUID ticketId = confidentialTicket.getId();

        // 3. Responsable B se connecte avec un vrai JWT
        String jwtB = loginAndGetJwt(userB.getEmail(), "Password123!");

        // 4. Responsable B tente d'accéder à l'historique du signalement confidentiel -> 404 Not Found
        mockMvc.perform(get(CONTEXT_PATH + "/responsable/tickets/" + ticketId + "/history")
                        .contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtB))
                .andExpect(status().isNotFound());

        // 5. Responsable B tente d'accéder aux pièces jointes -> 404 Not Found
        mockMvc.perform(get(CONTEXT_PATH + "/responsable/tickets/" + ticketId + "/attachments")
                        .contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtB))
                .andExpect(status().isNotFound());

        // 6. Responsable A accède avec succès -> 200 OK
        String jwtA = loginAndGetJwt(userA.getEmail(), "Password123!");
        mockMvc.perform(get(CONTEXT_PATH + "/responsable/tickets/" + ticketId + "/history")
                        .contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtA))
                .andExpect(status().isOk());

        // 7. Retrait immédiat de l'habilitation confidentielle de Responsable A en base
        accountA.setConfidentialHabilitation(false);
        responsableAccountRepository.save(accountA);

        // 8. Responsable A re-tente immédiatement avec le MÊME JWT -> 404 Not Found sans attendre l'expiration
        mockMvc.perform(get(CONTEXT_PATH + "/responsable/tickets/" + ticketId + "/history")
                        .contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtA))
                .andExpect(status().isNotFound());
    }
}
