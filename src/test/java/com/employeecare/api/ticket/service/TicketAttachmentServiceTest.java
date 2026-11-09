package com.employeecare.api.ticket.service;

import com.employeecare.api.generated.models.TicketAttachmentResponse;
import com.employeecare.api.responsable.entity.ResponsableAccount;
import com.employeecare.api.responsable.repository.ResponsableAccountRepository;
import com.employeecare.api.ticket.entity.Ticket;
import com.employeecare.api.ticket.entity.TicketAttachment;
import com.employeecare.api.ticket.entity.TicketCategory;
import com.employeecare.api.ticket.entity.TicketStatus;
import com.employeecare.api.ticket.repository.TicketAttachmentRepository;
import com.employeecare.api.ticket.repository.TicketRepository;
import com.employeecare.api.ticket.service.TicketAttachmentService.DownloadData;
import com.employeecare.api.ticket.storage.PrivateAttachmentStorage;
import com.employeecare.api.department.entity.Department;
import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketAttachmentServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketAttachmentRepository attachmentRepository;

    @Mock
    private PrivateAttachmentStorage storage;

    @Mock
    private ResponsableAccountRepository responsableRepository;

    @InjectMocks
    private TicketAttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("upload - Enregistre un PDF valide et retourne TicketAttachmentResponse")
    void upload_validPdf_shouldStoreAndSaveMetadata() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        AppUser employee = new AppUser();
        employee.setId(employeeId);

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setEmployee(employee);
        ticket.setStatus(TicketStatus.EN_ATTENTE);

        byte[] pdfBytes = "%PDF-1.7 contenu valide".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "capture_erreur.pdf",
                "application/pdf",
                pdfBytes
        );

        when(ticketRepository.findOwnedByIdForUpdate(ticketId, employeeId)).thenReturn(Optional.of(ticket));
        when(attachmentRepository.countByTicket_Id(ticketId)).thenReturn(0L);
        when(attachmentRepository.saveAndFlush(any(TicketAttachment.class))).thenAnswer(invocation -> {
            TicketAttachment att = invocation.getArgument(0);
            att.setCreatedAt(Instant.parse("2026-11-09T12:00:00Z"));
            return att;
        });

        TicketAttachmentResponse response = attachmentService.upload(employeeId, ticketId, file);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getFileName()).isEqualTo("capture_erreur.pdf");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getSizeBytes()).isEqualTo(pdfBytes.length);
        verify(storage).store(eq(response.getId()), eq(pdfBytes));
    }

    @Test
    @DisplayName("upload - Signalement clôturé lève 409 Conflict")
    void upload_whenTicketClosed_shouldThrow409() {
        UUID employeeId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setStatus(TicketStatus.CLOTURE);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "capture.pdf",
                "application/pdf",
                "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII)
        );

        when(ticketRepository.findOwnedByIdForUpdate(ticketId, employeeId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> attachmentService.upload(employeeId, ticketId, file))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("upload - Limite de 5 fichiers atteinte lève 409 Conflict")
    void upload_whenMaxFilesReached_shouldThrow409() {
        UUID employeeId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setStatus(TicketStatus.EN_ATTENTE);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "capture.pdf",
                "application/pdf",
                "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII)
        );

        when(ticketRepository.findOwnedByIdForUpdate(ticketId, employeeId)).thenReturn(Optional.of(ticket));
        when(attachmentRepository.countByTicket_Id(ticketId)).thenReturn(5L);

        assertThatThrownBy(() -> attachmentService.upload(employeeId, ticketId, file))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("upload - Signature binaire invalide lève 400 Bad Request")
    void upload_invalidMagicBytes_shouldThrow400() {
        UUID employeeId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setStatus(TicketStatus.EN_ATTENTE);

        MockMultipartFile fakePdf = new MockMultipartFile(
                "file",
                "malicious.pdf",
                "application/pdf",
                "NOT_A_REAL_PDF".getBytes(StandardCharsets.US_ASCII)
        );

        when(ticketRepository.findOwnedByIdForUpdate(ticketId, employeeId)).thenReturn(Optional.of(ticket));
        when(attachmentRepository.countByTicket_Id(ticketId)).thenReturn(0L);

        assertThatThrownBy(() -> attachmentService.upload(employeeId, ticketId, fakePdf))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("listForEmployee - Retourne les pièces jointes si l'employé est propriétaire")
    void listForEmployee_whenOwner_shouldReturnList() {
        UUID employeeId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        TicketAttachment attachment = new TicketAttachment();
        attachment.setId(attachmentId);
        attachment.setOriginalFilename("capture.png");
        attachment.setContentType("image/png");
        attachment.setSizeBytes(1024L);
        attachment.setCreatedAt(Instant.parse("2026-11-09T10:15:00Z"));

        when(ticketRepository.existsByIdAndEmployee_Id(ticketId, employeeId)).thenReturn(true);
        when(attachmentRepository.findForTicket(ticketId)).thenReturn(List.of(attachment));

        List<TicketAttachmentResponse> result = attachmentService.listForEmployee(employeeId, ticketId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(attachmentId);
        assertThat(result.get(0).getFileName()).isEqualTo("capture.png");
    }

    @Test
    @DisplayName("listForEmployee - Lève 404 si l'employé n'est pas propriétaire")
    void listForEmployee_whenNotOwner_shouldThrow404() {
        UUID employeeId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        when(ticketRepository.existsByIdAndEmployee_Id(ticketId, employeeId)).thenReturn(false);

        assertThatThrownBy(() -> attachmentService.listForEmployee(employeeId, ticketId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("downloadForEmployee - Retourne DownloadData lorsque la pièce jointe appartient au signalement")
    void downloadForEmployee_whenValid_shouldReturnDownloadData() {
        UUID employeeId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        TicketAttachment attachment = new TicketAttachment();
        attachment.setId(attachmentId);
        attachment.setOriginalFilename("rapport_incident.pdf");
        attachment.setContentType("application/pdf");
        attachment.setSizeBytes(2048L);

        Resource resource = new ByteArrayResource("%PDF-1.7".getBytes(StandardCharsets.US_ASCII));

        when(ticketRepository.existsByIdAndEmployee_Id(ticketId, employeeId)).thenReturn(true);
        when(attachmentRepository.findByIdAndTicket_Id(attachmentId, ticketId)).thenReturn(Optional.of(attachment));
        when(storage.load(attachmentId)).thenReturn(resource);

        DownloadData data = attachmentService.downloadForEmployee(employeeId, ticketId, attachmentId);

        assertThat(data.fileName()).isEqualTo("rapport_incident.pdf");
        assertThat(data.sizeBytes()).isEqualTo(2048L);
        assertThat(data.resource()).isSameAs(resource);
    }

    @Test
    @DisplayName("listForResponsable - Retourne les pièces jointes si le responsable est attribué et habilité")
    void listForResponsable_whenAuthorized_shouldReturnList() {
        UUID responsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        Department department = new Department();
        department.setId(departmentId);

        Role role = new Role();
        role.setCode("RESPONSABLE");

        AppUser user = new AppUser();
        user.setId(responsableId);
        user.setStatus(AccountStatus.ACTIF);
        user.setRoles(Set.of(role));

        TicketCategory category = new TicketCategory();
        category.setCode("TECHNIQUE");

        ResponsableAccount responsable = new ResponsableAccount();
        responsable.setUserId(responsableId);
        responsable.setUser(user);
        responsable.setActive(true);
        responsable.setDepartment(department);
        responsable.setAssignedCategories(Set.of("TECHNIQUE"));
        responsable.setConfidentialHabilitation(true);

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setAssignedResponsable(user);
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setConfidential(true);

        TicketAttachment attachment = new TicketAttachment();
        attachment.setId(attachmentId);
        attachment.setOriginalFilename("erreur.png");
        attachment.setContentType("image/png");
        attachment.setSizeBytes(512L);
        attachment.setCreatedAt(Instant.parse("2026-11-09T11:00:00Z"));

        when(responsableRepository.findById(responsableId)).thenReturn(Optional.of(responsable));
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(attachmentRepository.findForTicket(ticketId)).thenReturn(List.of(attachment));

        List<TicketAttachmentResponse> result = attachmentService.listForResponsable(responsableId, ticketId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(attachmentId);
    }

    @Test
    @DisplayName("downloadForResponsable - Lève 404 si le signalement n'est pas attribué à ce responsable")
    void downloadForResponsable_whenNotAssigned_shouldThrow404() {
        UUID responsableId = UUID.randomUUID();
        UUID otherResponsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        Department department = new Department();
        department.setId(departmentId);

        Role role = new Role();
        role.setCode("RESPONSABLE");

        AppUser user = new AppUser();
        user.setId(responsableId);
        user.setStatus(AccountStatus.ACTIF);
        user.setRoles(Set.of(role));

        AppUser otherUser = new AppUser();
        otherUser.setId(otherResponsableId);

        TicketCategory category = new TicketCategory();
        category.setCode("TECHNIQUE");

        ResponsableAccount responsable = new ResponsableAccount();
        responsable.setUserId(responsableId);
        responsable.setUser(user);
        responsable.setActive(true);
        responsable.setDepartment(department);
        responsable.setAssignedCategories(Set.of("TECHNIQUE"));
        responsable.setConfidentialHabilitation(true);

        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setAssignedResponsable(otherUser);
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setConfidential(false);

        when(responsableRepository.findById(responsableId)).thenReturn(Optional.of(responsable));
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> attachmentService.downloadForResponsable(responsableId, ticketId, attachmentId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
