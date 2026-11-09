package com.employeecare.api.ticket.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class PrivateAttachmentStorage {

    private static final Logger log = LoggerFactory.getLogger(
            PrivateAttachmentStorage.class
    );

    private final Path directory;

    public PrivateAttachmentStorage(
            @Value("${employeecare.attachments.directory}")
            String storageDirectory
    ) {
        this.directory = Path.of(storageDirectory)
                .toAbsolutePath()
                .normalize();
    }

    public void store(
            UUID attachmentId,
            byte[] content
    ) throws IOException {

        Files.createDirectories(directory);

        Path target = directory.resolve(
                attachmentId.toString()
        );

        Files.write(
                target,
                content,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );
    }

    public void deleteQuietly(UUID attachmentId) {

        Path target = directory.resolve(
                attachmentId.toString()
        );

        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            log.error(
                    "Impossible de supprimer le fichier privé {}",
                    attachmentId,
                    exception
            );
        }
    }
}
