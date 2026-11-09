package com.employeecare.api.ticket.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrivateAttachmentStorageTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("store, load et deleteQuietly - Écrit, lit puis supprime le fichier privé par UUID")
    void storeLoadAndDelete_shouldWriteReadAndRemoveFile() throws IOException {
        PrivateAttachmentStorage storage = new PrivateAttachmentStorage(tempDir.toString());
        UUID attachmentId = UUID.randomUUID();
        byte[] content = "%PDF-1.7 sample".getBytes(StandardCharsets.US_ASCII);

        storage.store(attachmentId, content);

        Path storedFile = tempDir.resolve(attachmentId.toString());
        assertThat(Files.exists(storedFile)).isTrue();
        assertThat(Files.readAllBytes(storedFile)).isEqualTo(content);

        Resource loaded = storage.load(attachmentId);
        assertThat(loaded.exists()).isTrue();
        assertThat(loaded.getContentAsByteArray()).isEqualTo(content);

        storage.deleteQuietly(attachmentId);
        assertThat(Files.exists(storedFile)).isFalse();
    }

    @Test
    @DisplayName("load - Lève 503 Service Unavailable lorsque le fichier physique est introuvable")
    void load_whenMissing_shouldThrow503() {
        PrivateAttachmentStorage storage = new PrivateAttachmentStorage(tempDir.toString());
        UUID missingId = UUID.randomUUID();

        assertThatThrownBy(() -> storage.load(missingId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
