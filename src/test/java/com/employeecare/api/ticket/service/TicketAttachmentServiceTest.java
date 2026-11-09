package com.employeecare.api.ticket.service;

import com.employeecare.api.generated.models.TicketAttachmentResponse;
import com.employeecare.api.ticket.entity.Ticket;
import com.employeecare.api.ticket.entity.TicketAttachment;
import com.employeecare.api.ticket.entity.TicketStatus;
import com.employeecare.api.ticket.repository.TicketAttachmentRepository;
import com.employeecare.api.ticket.repository.TicketRepository;
import com.employeecare.api.ticket.storage.PrivateAttachmentStorage;
import com.employeecare.api.user.entity.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
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
}
