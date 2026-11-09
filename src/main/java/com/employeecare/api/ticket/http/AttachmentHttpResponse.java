package com.employeecare.api.ticket.http;

import com.employeecare.api.ticket.service.TicketAttachmentService.DownloadData;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

public final class AttachmentHttpResponse {

    private AttachmentHttpResponse() {
    }

    public static ResponseEntity<Resource> download(
            DownloadData data
    ) {

        String disposition = ContentDisposition
                .attachment()
                .filename(
                        data.fileName(),
                        StandardCharsets.UTF_8
                )
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(
                        MediaType.APPLICATION_OCTET_STREAM
                )
                .contentLength(
                        data.sizeBytes()
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition
                )
                .header(
                        HttpHeaders.CACHE_CONTROL,
                        "private, no-store, max-age=0"
                )
                .header(
                        "X-Content-Type-Options",
                        "nosniff"
                )
                .body(
                        data.resource()
                );
    }
}
