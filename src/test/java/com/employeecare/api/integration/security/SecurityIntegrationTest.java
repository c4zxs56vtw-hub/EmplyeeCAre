package com.employeecare.api.integration.security;

import com.employeecare.api.integration.config.PostgresIntegrationTestConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    private static final String CONTEXT_PATH = "/api";

    private MockHttpServletRequestBuilder getApi(String path) {
        return get(CONTEXT_PATH + path)
                .contextPath(CONTEXT_PATH);
    }

    private MockHttpServletRequestBuilder postApi(String path) {
        return post(CONTEXT_PATH + path)
                .contextPath(CONTEXT_PATH);
    }

    @Test
    void allFlywayMigrationsMustBeApplied() {
        // Vérifier qu'aucune migration n'est en attente.
        assertEquals(0, flyway.info().pending().length);

        // Vérifier que la migration V12 est présente.
        boolean anonymousMigrationApplied = Arrays.stream(flyway.info().applied())
                .anyMatch(info -> info.getVersion() != null && "12".equals(info.getVersion().getVersion()));
        assertTrue(anonymousMigrationApplied);

        // Vérifier que les tables principales existent.
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                    'tickets',
                    'ticket_events',
                    'anonymous_ticket_access'
                  )
                """, Integer.class);
        assertEquals(3, tableCount);
    }

    @Test
    void employeeTicketsWithoutJwtMustReturn401() throws Exception {
        mockMvc.perform(getApi("/employe/tickets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidJwtMustReturn401() throws Exception {
        mockMvc.perform(getApi("/employe/tickets")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.signature"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCannotAccessAdminDashboard() throws Exception {
        UUID employeeId = UUID.randomUUID();

        mockMvc.perform(getApi("/admin/dashboard/stats")
                .with(jwt()
                        .jwt(token -> token.subject(employeeId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void responsableCannotAccessEmployeeTickets() throws Exception {
        UUID responsableId = UUID.randomUUID();

        mockMvc.perform(getApi("/employe/tickets")
                .with(jwt()
                        .jwt(token -> token.subject(responsableId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_RESPONSABLE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void responsableCannotUseEmployeeAttachmentEndpoint() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID responsableId = UUID.randomUUID();

        String path = "/employe/tickets/" + ticketId + "/attachments/" + attachmentId + "/download";

        mockMvc.perform(getApi(path)
                .with(jwt()
                        .jwt(token -> token.subject(responsableId.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_RESPONSABLE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownAnonymousReferenceMustReturn404() throws Exception {
        String unknownReference = "X-" + UUID.randomUUID();

        String requestBody = """
                {
                  "reference": "%s",
                  "accessCode": "code-secret-de-test-invalide"
                }
                """.formatted(unknownReference);

        mockMvc.perform(postApi("/public/tickets/anonymous/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidAnonymousCloseMustNotCreateEvents() throws Exception {
        // ==========================================
        // 1. COMPTER LES EVENEMENTS AVANT
        // ==========================================
        Long eventsBefore = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ticket_events
                """, Long.class);

        // ==========================================
        // 2. PREPARER UNE REFERENCE INEXISTANTE
        // ==========================================
        String unknownReference = "X-" + UUID.randomUUID();

        String requestBody = """
                {
                  "reference": "%s",
                  "accessCode": "code-secret-incorrect"
                }
                """.formatted(unknownReference);

        // ==========================================
        // 3. TENTER UNE CLOTURE FRAUDULEUSE
        // ==========================================
        mockMvc.perform(postApi("/public/tickets/anonymous/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isNotFound());

        // ==========================================
        // 4. COMPTER LES EVENEMENTS APRES
        // ==========================================
        Long eventsAfter = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ticket_events
                """, Long.class);

        // ==========================================
        // 5. VERIFIER L'ABSENCE DE MODIFICATION
        // ==========================================
        assertEquals(eventsBefore, eventsAfter);
    }
}
