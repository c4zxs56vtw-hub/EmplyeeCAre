package com.employeecare.api.user.service;

import com.employeecare.api.auth.service.ActivationTokenService;
import com.employeecare.api.department.entity.Department;
import com.employeecare.api.department.repository.DepartmentRepository;
import com.employeecare.api.generated.models.CreateEmployeeRequest;
import com.employeecare.api.generated.models.UserRole;
import com.employeecare.api.notification.email.AccountInvitationRequestedEvent;
import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;
import com.employeecare.api.user.mapper.AppUserMapper;
import com.employeecare.api.user.repository.AppUserRepository;
import com.employeecare.api.user.repository.RoleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AppUserMapper appUserMapper;
    @Mock private ActivationTokenService activationTokenService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private EmployeeService service;

    private final UUID departmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EmployeeService(
                appUserRepository,
                departmentRepository,
                roleRepository,
                appUserMapper,
                activationTokenService,
                eventPublisher
        );
    }

    @Test
    void createEmployee_employe_enregistreGenereJetonEtPublieEvenement() {

        CreateEmployeeRequest request = validRequest(UserRole.EMPLOYE);
        Department department = department(true);

        when(appUserRepository.existsByEmailIgnoreCase("claire.moreau@entreprise.com"))
                .thenReturn(false);
        when(departmentRepository.findById(departmentId))
                .thenReturn(Optional.of(department));
        when(roleRepository.findById("EMPLOYE"))
                .thenReturn(Optional.of(role("EMPLOYE")));
        when(appUserMapper.toEntity(request))
                .thenReturn(mappedUser());
        when(appUserRepository.save(any(AppUser.class)))
                .thenAnswer(invocation -> {
                    AppUser u = invocation.getArgument(0);
                    u.setId(UUID.randomUUID());
                    return u;
                });
        when(activationTokenService.generateToken(any(AppUser.class)))
                .thenReturn("raw-token");

        AppUser result = service.createEmployee(request);

        // Données métier
        assertThat(result.getId()).isNotNull();
        assertThat(result.getEmail()).isEqualTo("claire.moreau@entreprise.com");
        assertThat(result.getDepartment()).isSameAs(department);
        assertThat(result.getStatus()).isEqualTo(AccountStatus.EN_ATTENTE_ACTIVATION);
        assertThat(result.getEmployeeNumber()).startsWith("EC-");
        assertThat(result.getRoles())
                .extracting(Role::getCode)
                .containsExactly("EMPLOYE");

        // Jeton généré pour l'utilisateur enregistré
        verify(activationTokenService).generateToken(result);

        // Événement d'invitation publié
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getValue())
                .isEqualTo(new AccountInvitationRequestedEvent(
                        "claire.moreau@entreprise.com",
                        "Claire",
                        "raw-token"
                ));
    }

    @Test
    void createEmployee_responsable_attribueEmployeEtResponsable() {

        CreateEmployeeRequest request = validRequest(UserRole.RESPONSABLE);

        when(appUserRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(departmentRepository.findById(departmentId))
                .thenReturn(Optional.of(department(true)));
        when(roleRepository.findById("EMPLOYE"))
                .thenReturn(Optional.of(role("EMPLOYE")));
        when(roleRepository.findById("RESPONSABLE"))
                .thenReturn(Optional.of(role("RESPONSABLE")));
        when(appUserMapper.toEntity(request)).thenReturn(mappedUser());
        when(appUserRepository.save(any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(activationTokenService.generateToken(any())).thenReturn("t");

        AppUser result = service.createEmployee(request);

        assertThat(result.getRoles())
                .extracting(Role::getCode)
                .containsExactlyInAnyOrder("EMPLOYE", "RESPONSABLE");
    }

    @Test
    void createEmployee_emailNormalise() {

        CreateEmployeeRequest request = validRequest(UserRole.EMPLOYE);
        request.setEmail("  Claire.MOREAU@Entreprise.com ");

        when(appUserRepository.existsByEmailIgnoreCase("claire.moreau@entreprise.com"))
                .thenReturn(false);
        when(departmentRepository.findById(departmentId))
                .thenReturn(Optional.of(department(true)));
        when(roleRepository.findById("EMPLOYE"))
                .thenReturn(Optional.of(role("EMPLOYE")));
        when(appUserMapper.toEntity(request)).thenReturn(mappedUser());
        when(appUserRepository.save(any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(activationTokenService.generateToken(any())).thenReturn("t");

        AppUser result = service.createEmployee(request);

        assertThat(result.getEmail()).isEqualTo("claire.moreau@entreprise.com");
    }

    @Test
    void createEmployee_requeteNull_leveException() {

        assertThatThrownBy(() -> service.createEmployee(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obligatoires");

        verifyNoInteractions(appUserRepository, activationTokenService, eventPublisher);
    }

    @Test
    void createEmployee_champObligatoireManquant_leveException() {

        CreateEmployeeRequest request = validRequest(UserRole.EMPLOYE);
        request.setFirstName("  ");

        assertThatThrownBy(() -> service.createEmployee(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obligatoires");
    }

    @Test
    void createEmployee_emailDejaUtilise_leveException() {

        CreateEmployeeRequest request = validRequest(UserRole.EMPLOYE);

        when(appUserRepository.existsByEmailIgnoreCase(any())).thenReturn(true);

        assertThatThrownBy(() -> service.createEmployee(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("déjà utilisée");

        verify(appUserRepository, never()).save(any());
        verifyNoInteractions(activationTokenService, eventPublisher);
    }

    @Test
    void createEmployee_roleAdministrateur_leveException() {

        CreateEmployeeRequest request = validRequest(UserRole.ADMINISTRATEUR);

        when(appUserRepository.existsByEmailIgnoreCase(any())).thenReturn(false);

        assertThatThrownBy(() -> service.createEmployee(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("administrateur");

        verify(appUserRepository, never()).save(any());
    }

    @Test
    void createEmployee_categoriesNonSupportees_leveException() {

        CreateEmployeeRequest request = validRequest(UserRole.RESPONSABLE);
        request.setAssignedCategories(List.of("Informatique"));

        when(appUserRepository.existsByEmailIgnoreCase(any())).thenReturn(false);

        assertThatThrownBy(() -> service.createEmployee(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catégories");
    }

    @Test
    void createEmployee_departementIdInvalide_leveException() {

        CreateEmployeeRequest request = validRequest(UserRole.EMPLOYE);
        request.setDepartmentId("dept-rh");

        when(appUserRepository.existsByEmailIgnoreCase(any())).thenReturn(false);

        assertThatThrownBy(() -> service.createEmployee(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("département est invalide");
    }

    @Test
    void createEmployee_departementIntrouvable_leveException() {

        CreateEmployeeRequest request = validRequest(UserRole.EMPLOYE);

        when(appUserRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createEmployee(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("n'existe pas");
    }

    @Test
    void createEmployee_departementDesactive_leveException() {

        CreateEmployeeRequest request = validRequest(UserRole.EMPLOYE);

        when(appUserRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(departmentRepository.findById(departmentId))
                .thenReturn(Optional.of(department(false)));

        assertThatThrownBy(() -> service.createEmployee(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("désactivé");

        verify(appUserRepository, never()).save(any());
    }

    // ===== Utilitaires =====

    private CreateEmployeeRequest validRequest(UserRole role) {
        CreateEmployeeRequest request = new CreateEmployeeRequest();
        request.setFirstName("Claire");
        request.setLastName("Moreau");
        request.setEmail("claire.moreau@entreprise.com");
        request.setPosition("Chargée de développement RH");
        request.setDepartmentId(departmentId.toString());
        request.setRole(role);
        return request;
    }

    private Department department(boolean active) {
        Department department = new Department();
        department.setId(departmentId);
        department.setName("Ressources Humaines");
        department.setActive(active);
        return department;
    }

    private static Role role(String code) {
        Role role = new Role();
        role.setCode(code);
        return role;
    }

    private static AppUser mappedUser() {
        AppUser user = new AppUser();
        user.setFirstName("Claire");
        user.setLastName("Moreau");
        user.setJobTitle("Chargée de développement RH");
        return user;
    }
}
