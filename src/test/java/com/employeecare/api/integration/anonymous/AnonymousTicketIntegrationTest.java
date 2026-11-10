package com.employeecare.api.integration.anonymous;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.department.repository.DepartmentRepository;
import com.employeecare.api.integration.config.PostgresIntegrationTestConfig;
import com.employeecare.api.ticket.entity.Ticket;
import com.employeecare.api.ticket.entity.TicketCategory;
import com.employeecare.api.ticket.entity.TicketStatus;
import com.employeecare.api.ticket.repository.TicketCategoryRepository;
import com.employeecare.api.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresIntegrationTestConfig.class)
class AnonymousTicketIntegrationTest {

    private static final String CONTEXT_PATH = "/api";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private TicketCategoryRepository categoryRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Department department;
    private TicketCategory category;

    @BeforeEach
    void setUp() {
        department = departmentRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    Department dep = new Department();
                    dep.setName("Direction Générale " + UUID.randomUUID());
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
    }

    private String extractJsonField(String json, String fieldName) {
        String key = "\"" + fieldName + "\":";
        int index = json.indexOf(key);
        if (index == -1) {
            throw new IllegalArgumentException("Field not found: " + fieldName);
        }
        int start = json.indexOf("\"", index + key.length()) + 1;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    @Test
    @DisplayName("Scénario D : Signalement anonyme existant - bon code (200), mauvais code (404), tentative clôture (404 sans effet BD)")
    void anonymousTicketConsultationAndFraudulentClose() throws Exception {
        // 1. Créer un véritable signalement anonyme via l'endpoint public
        String createRequestBody = """
                {
                    "title": "Signalement anonyme de test sécurité",
                    "description": "Description détaillée pour test d'intégration",
                    "departmentId": "%s",
                    "categoryCode": "%s"
                }
                """.formatted(department.getId(), category.getCode());

        String createdJson = mockMvc.perform(
                        post(CONTEXT_PATH + "/public/tickets/anonymous")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createRequestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").isNotEmpty())
                .andExpect(jsonPath("$.accessCode").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String reference = extractJsonField(createdJson, "reference");
        String accessCode = extractJsonField(createdJson, "accessCode");

        // 2. Consultation avec le bon code secret -> 200 OK
        String validLookupBody = """
                {
                    "reference": "%s",
                    "accessCode": "%s"
                }
                """.formatted(reference, accessCode);

        mockMvc.perform(
                        post(CONTEXT_PATH + "/public/tickets/anonymous/lookup")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validLookupBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(reference))
                .andExpect(jsonPath("$.title").value("Signalement anonyme de test sécurité"));

        // 3. Consultation avec un MAUVAIS code secret -> 404 Not Found
        String invalidLookupBody = """
                {
                    "reference": "%s",
                    "accessCode": "mauvais-code-secret-frauduleux"
                }
                """.formatted(reference);

        mockMvc.perform(
                        post(CONTEXT_PATH + "/public/tickets/anonymous/lookup")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidLookupBody))
                .andExpect(status().isNotFound());

        // 4. Tentative frauduleuse de clôture avec un MAUVAIS code secret
        Long eventsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_events",
                Long.class
        );

        String invalidCloseBody = """
                {
                    "reference": "%s",
                    "accessCode": "mauvais-code-secret-frauduleux"
                }
                """.formatted(reference);

        mockMvc.perform(
                        post(CONTEXT_PATH + "/public/tickets/anonymous/close")
                                .contextPath(CONTEXT_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidCloseBody))
                .andExpect(status().isNotFound());

        // 5. Vérifier l'intégrité de la base : aucun nouvel événement et statut inchangé
        Long eventsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_events",
                Long.class
        );
        assertEquals(eventsBefore, eventsAfter);

        Ticket reloadedTicket = ticketRepository.findByReferenceAndAnonymousTrue(reference).orElseThrow();
        assertThat(reloadedTicket.getStatus()).isEqualTo(TicketStatus.EN_ATTENTE);
    }
}
