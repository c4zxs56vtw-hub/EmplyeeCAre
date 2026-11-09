package com.employeecare.api.ticket.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateAttachmentStorageTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("store et deleteQuietly - Écrit puis supprime le fichier privé par UUID")
    void storeAndDelete_shouldWriteAndRemoveFile() throws IOException {
        PrivateAttachmentStorage storage = new PrivateAttachmentStorage(tempDir.toString());
        UUID attachmentId = UUID.randomUUID();
        byte[] content = "%PDF-1.7 sample".getBytes(StandardCharsets.US_ASCII);

        storage.store(attachmentId, content);

        Path storedFile = tempDir.resolve(attachmentId.toString());
        assertThat(Files.exists(storedFile)).isTrue();
        assertThat(Files.readAllBytes(storedFile)).isEqualTo(content);

        storage.deleteQuietly(attachmentId);
        assertThat(Files.exists(storedFile)).isFalse();
    }
}
