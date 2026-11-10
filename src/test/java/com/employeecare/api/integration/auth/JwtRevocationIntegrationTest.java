package com.employeecare.api.integration.auth;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.department.repository.DepartmentRepository;
import com.employeecare.api.integration.config.PostgresIntegrationTestConfig;
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
class JwtRevocationIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    private Department department;
    private Role employeRole;

    @BeforeEach
    void setUp() {
        department = departmentRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    Department dep = new Department();
                    dep.setName("Département Sécurité " + UUID.randomUUID());
                    dep.setActive(true);
                    return departmentRepository.save(dep);
                });

        employeRole = roleRepository.findById("EMPLOYE").orElseThrow();
    }

    private AppUser createActiveUser(String emailPrefix) {
        String email = emailPrefix + "-" + UUID.randomUUID() + "@employeecare.local";
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFirstName("Sophie");
        user.setLastName("Legrand");
        user.setEmployeeNumber("EMP-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setStatus(AccountStatus.ACTIF);
        user.setSessionVersion(1L);
        user.setDepartment(department);
        user.setRoles(Collections.singleton(employeRole));
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
    @DisplayName("Scénario C : Révocation du JWT (logout), changement de session_version et désactivation de compte -> 401")
    void revokedJwtOrDeactivatedUserMustReturn401() throws Exception {
        // 1. Créer un employé actif
        AppUser user = createActiveUser("emp-jwt-test");

        // 2. Connexion et obtention d'un JWT valide
        String jwtToken1 = loginAndGetJwt(user.getEmail(), "Password123!");

        // 3. Appel de l'espace employé -> 200 OK
        mockMvc.perform(get(CONTEXT_PATH + "/employe/tickets")
                        .contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken1))
                .andExpect(status().isOk());

        // 4. Révocation du JWT via /api/auth/logout
        mockMvc.perform(post(CONTEXT_PATH + "/auth/logout")
                        .contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken1))
                .andExpect(status().isOk());

        // 5. Réutilisation du même JWT révoqué -> 401 Unauthorized
        mockMvc.perform(get(CONTEXT_PATH + "/employe/tickets")
                        .contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken1))
                .andExpect(status().isUnauthorized());

        // 6. Test invalidation par session_version : l'employé se reconnecte
        String jwtToken2 = loginAndGetJwt(user.getEmail(), "Password123!");
        mockMvc.perform(get(CONTEXT_PATH + "/employe/tickets")
                        .contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken2))
                .andExpect(status().isOk());

        // L'administrateur ou une réinitialisation incrémente la session_version en base
        AppUser userToUpdate = appUserRepository.findById(user.getId()).orElseThrow();
        userToUpdate.setSessionVersion(userToUpdate.getSessionVersion() + 1);
        appUserRepository.save(userToUpdate);

        // L'ancien jeton token2 doit être rejeté immédiatement -> 401 Unauthorized
        mockMvc.perform(get(CONTEXT_PATH + "/employe/tickets")
                        .contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken2))
                .andExpect(status().isUnauthorized());

        // 7. Test désactivation de compte : reconnexion
        String jwtToken3 = loginAndGetJwt(user.getEmail(), "Password123!");
        mockMvc.perform(get(CONTEXT_PATH + "/employe/tickets")
                        .contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken3))
                .andExpect(status().isOk());

        // Le compte est suspendu / désactivé en base
        userToUpdate = appUserRepository.findById(user.getId()).orElseThrow();
        userToUpdate.setStatus(AccountStatus.DESACTIVE);
        appUserRepository.save(userToUpdate);

        // Le jeton devient immédiatement inutilisable -> 401 Unauthorized
        mockMvc.perform(get(CONTEXT_PATH + "/employe/tickets")
                        .contextPath(CONTEXT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken3))
                .andExpect(status().isUnauthorized());
    }
}
