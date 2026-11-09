package com.employeecare.api.ticket.service;

import com.employeecare.api.generated.models.TicketAttachmentResponse;
import com.employeecare.api.ticket.entity.Ticket;
import com.employeecare.api.ticket.entity.TicketAttachment;
import com.employeecare.api.ticket.entity.TicketStatus;
import com.employeecare.api.ticket.repository.TicketAttachmentRepository;
import com.employeecare.api.ticket.repository.TicketRepository;
import com.employeecare.api.ticket.storage.PrivateAttachmentStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

@Service
public class TicketAttachmentService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_FILES_PER_TICKET = 5;

    private final TicketRepository ticketRepository;
    private final TicketAttachmentRepository attachmentRepository;
    private final PrivateAttachmentStorage storage;

    public TicketAttachmentService(
            TicketRepository ticketRepository,
            TicketAttachmentRepository attachmentRepository,
            PrivateAttachmentStorage storage
    ) {
        this.ticketRepository = ticketRepository;
        this.attachmentRepository = attachmentRepository;
        this.storage = storage;
    }

    @Transactional
    public TicketAttachmentResponse upload(
            UUID employeeId,
            UUID ticketId,
            MultipartFile file
    ) {

        // 1. Vérifier le propriétaire du signalement.

        Ticket ticket = ticketRepository
                .findOwnedByIdForUpdate(ticketId, employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Signalement introuvable."
                ));

        // 2. Interdire les ajouts après clôture.

        if (ticket.getStatus() == TicketStatus.CLOTURE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Impossible d'ajouter une pièce jointe à un signalement clôturé."
            );
        }

        // 3. Vérifier le nombre de fichiers.

        long count = attachmentRepository.countByTicket_Id(ticketId);

        if (count >= MAX_FILES_PER_TICKET) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ce signalement possède déjà cinq pièces jointes."
            );
        }

        // 4. Vérifier la présence et la taille du fichier.

        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Fichier vide ou taille supérieure à 5 Mo."
            );
        }

        // 5. Nettoyer le nom original.

        String originalName = file.getOriginalFilename();

        if (originalName == null) {
            originalName = "";
        }

        originalName = originalName.replace('\\', '/');
        originalName = originalName.substring(
                originalName.lastIndexOf('/') + 1
        );
        originalName = originalName.replaceAll(
                "[\\p{Cntrl}]",
                ""
        );

        if (originalName.isBlank() || originalName.length() > 180) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nom de fichier invalide."
            );
        }

        // 6. Lire le contenu.

        byte[] content;

        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Impossible de lire le fichier.",
                    exception
            );
        }

        if (content.length == 0 || content.length > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Taille du fichier invalide."
            );
        }

        // 7. Vérifier extension et signature du fichier.

        String contentType = detectContentType(
                originalName,
                content
        );

        // 8. Générer un identifiant indépendant du nom original.

        UUID attachmentId = UUID.randomUUID();

        // 9. Enregistrer le fichier dans le stockage privé.

        try {
            storage.store(attachmentId, content);
        } catch (IOException exception) {
            storage.deleteQuietly(attachmentId);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Impossible d'enregistrer la pièce jointe.",
                    exception
            );
        }

        // Supprimer le fichier physique si la transaction
        // PostgreSQL échoue ensuite.

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) {
                            storage.deleteQuietly(attachmentId);
                        }
                    }
                }
        );

        // 10. Enregistrer les métadonnées.

        TicketAttachment attachment = new TicketAttachment();

        attachment.setId(attachmentId);
        attachment.setTicket(ticket);
        attachment.setUploadedBy(ticket.getEmployee());
        attachment.setOriginalFilename(originalName);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(content.length);

        TicketAttachment saved = attachmentRepository.saveAndFlush(attachment);

        // 11. Construire la réponse.

        TicketAttachmentResponse response = new TicketAttachmentResponse();

        response.setId(saved.getId());
        response.setFileName(saved.getOriginalFilename());
        response.setContentType(saved.getContentType());
        response.setSizeBytes(saved.getSizeBytes());
        response.setUploadedAt(
                OffsetDateTime.ofInstant(
                        saved.getCreatedAt(),
                        ZoneOffset.UTC
                )
        );

        return response;
    }

    private String detectContentType(
            String fileName,
            byte[] content
    ) {

        int dot = fileName.lastIndexOf('.');

        if (dot <= 0 || dot == fileName.length() - 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Extension de fichier invalide."
            );
        }

        String extension = fileName
                .substring(dot + 1)
                .toLowerCase(Locale.ROOT);

        byte[] pdfSignature = "%PDF-".getBytes(StandardCharsets.US_ASCII);
        byte[] pngSignature = {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };
        byte[] jpegSignature = {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
        };

        String contentType = switch (extension) {
            case "pdf" ->
                    startsWith(content, pdfSignature)
                            ? "application/pdf"
                            : null;
            case "png" ->
                    startsWith(content, pngSignature)
                            ? "image/png"
                            : null;
            case "jpg", "jpeg" ->
                    startsWith(content, jpegSignature)
                            ? "image/jpeg"
                            : null;
            default -> null;
        };

        if (contentType == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Format non autorisé ou contenu invalide."
            );
        }

        return contentType;
    }

    private boolean startsWith(
            byte[] content,
            byte[] signature
    ) {
        if (content.length < signature.length) {
            return false;
        }

        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }

        return true;
    }
}
