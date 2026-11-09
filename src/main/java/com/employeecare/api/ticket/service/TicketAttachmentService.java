package com.employeecare.api.ticket.service;

import com.employeecare.api.generated.models.TicketAttachmentResponse;
import com.employeecare.api.responsable.entity.ResponsableAccount;
import com.employeecare.api.responsable.repository.ResponsableAccountRepository;
import com.employeecare.api.ticket.entity.Ticket;
import com.employeecare.api.ticket.entity.TicketAttachment;
import com.employeecare.api.ticket.entity.TicketStatus;
import com.employeecare.api.ticket.repository.TicketAttachmentRepository;
import com.employeecare.api.ticket.repository.TicketRepository;
import com.employeecare.api.ticket.storage.PrivateAttachmentStorage;
import com.employeecare.api.user.entity.AccountStatus;
import org.springframework.core.io.Resource;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TicketAttachmentService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_FILES_PER_TICKET = 5;

    private final TicketRepository ticketRepository;
    private final TicketAttachmentRepository attachmentRepository;
    private final PrivateAttachmentStorage storage;
    private final ResponsableAccountRepository responsableRepository;

    public TicketAttachmentService(
            TicketRepository ticketRepository,
            TicketAttachmentRepository attachmentRepository,
            PrivateAttachmentStorage storage,
            ResponsableAccountRepository responsableRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.attachmentRepository = attachmentRepository;
        this.storage = storage;
        this.responsableRepository = responsableRepository;
    }

    public record DownloadData(
            Resource resource,
            String fileName,
            long sizeBytes
    ) {
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

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TicketAttachmentResponse> listForEmployee(
            UUID employeeId,
            UUID ticketId
    ) {

        // 1. Vérifier que le signalement appartient à l'employé.

        verifyEmployeeAccess(employeeId, ticketId);

        // 2. Récupérer les pièces jointes.

        return attachmentRepository
                .findForTicket(ticketId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void verifyEmployeeAccess(
            UUID employeeId,
            UUID ticketId
    ) {
        boolean isOwner = ticketRepository
                .existsByIdAndEmployee_Id(
                        ticketId,
                        employeeId
                );

        if (!isOwner) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Signalement introuvable."
            );
        }
    }

    @Transactional(readOnly = true)
    public List<TicketAttachmentResponse> listForResponsable(
            UUID responsableId,
            UUID ticketId
    ) {

        // 1. Vérifier toutes les habilitations.

        verifyResponsableAccess(responsableId, ticketId);

        // 2. Retourner les pièces jointes autorisées.

        return attachmentRepository
                .findForTicket(ticketId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void verifyResponsableAccess(
            UUID responsableId,
            UUID ticketId
    ) {

        // 1. Rechercher le profil responsable.

        ResponsableAccount responsable = responsableRepository
                .findById(responsableId)
                .orElseThrow(this::notFound);

        // 2. Vérifier que le profil est actif.

        if (!responsable.isActive()) {
            throw notFound();
        }

        // 3. Vérifier le compte et son rôle.

        if (responsable.getUser().getStatus() != AccountStatus.ACTIF) {
            throw notFound();
        }

        boolean hasRole = responsable.getUser()
                .getRoles()
                .stream()
                .anyMatch(role ->
                        "RESPONSABLE".equals(role.getCode())
                );

        if (!hasRole) {
            throw notFound();
        }

        // 4. Rechercher le signalement.

        Ticket ticket = ticketRepository
                .findById(ticketId)
                .orElseThrow(this::notFound);

        // 5. Vérifier le responsable attribué.

        boolean isAssigned = ticket.getAssignedResponsable() != null
                && ticket.getAssignedResponsable()
                .getId()
                .equals(responsableId);

        // 6. Vérifier le département.

        boolean sameDepartment = responsable.getDepartment() != null
                && responsable.getDepartment()
                .getId()
                .equals(ticket.getDepartment().getId());

        // 7. Vérifier la catégorie.

        boolean allowedCategory = responsable.getAssignedCategories()
                .contains(ticket.getCategory().getCode());

        // 8. Vérifier la confidentialité.

        boolean confidentialAllowed = !ticket.isConfidential()
                || responsable.isConfidentialHabilitation();

        // 9. Refuser si une condition n'est pas respectée.

        if (!isAssigned
                || !sameDepartment
                || !allowedCategory
                || !confidentialAllowed) {
            throw notFound();
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Signalement introuvable."
        );
    }

    private TicketAttachmentResponse toResponse(
            TicketAttachment attachment
    ) {
        TicketAttachmentResponse response =
                new TicketAttachmentResponse();

        response.setId(
                attachment.getId()
        );
        response.setFileName(
                attachment.getOriginalFilename()
        );
        response.setContentType(
                attachment.getContentType()
        );
        response.setSizeBytes(
                attachment.getSizeBytes()
        );
        response.setUploadedAt(
                OffsetDateTime.ofInstant(
                        attachment.getCreatedAt(),
                        ZoneOffset.UTC
                )
        );

        return response;
    }

    @Transactional(readOnly = true)
    public DownloadData downloadForEmployee(
            UUID employeeId,
            UUID ticketId,
            UUID attachmentId
    ) {
        // Vérifier le propriétaire avant de récupérer le fichier.
        verifyEmployeeAccess(employeeId, ticketId);

        return prepareDownload(ticketId, attachmentId);
    }

    @Transactional(readOnly = true)
    public DownloadData downloadForResponsable(
            UUID responsableId,
            UUID ticketId,
            UUID attachmentId
    ) {
        // Vérifier toutes les permissions du responsable.
        verifyResponsableAccess(responsableId, ticketId);

        return prepareDownload(ticketId, attachmentId);
    }

    private DownloadData prepareDownload(
            UUID ticketId,
            UUID attachmentId
    ) {

        // 1. Vérifier que le fichier appartient au signalement.

        TicketAttachment attachment = attachmentRepository
                .findByIdAndTicket_Id(
                        attachmentId,
                        ticketId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Pièce jointe introuvable."
                ));

        // 2. Charger le fichier privé.

        Resource resource = storage.load(
                attachment.getId()
        );

        // 3. Nettoyer le nom du fichier pour la réponse HTTP.

        String fileName = attachment
                .getOriginalFilename()
                .replace('\\', '/');

        fileName = fileName.substring(
                fileName.lastIndexOf('/') + 1
        );

        fileName = fileName
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();

        if (fileName.isBlank()) {
            fileName = "piece-jointe";
        }

        // 4. Retourner les informations nécessaires.

        return new DownloadData(
                resource,
                fileName,
                attachment.getSizeBytes()
        );
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
