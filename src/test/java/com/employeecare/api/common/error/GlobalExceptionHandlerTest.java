package com.employeecare.api.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/employe/tickets");
    }

    @Test
    @DisplayName("handleResponseStatus - Retourne le code HTTP exact et le message métier explicite")
    void handleResponseStatus_shouldReturnExplicitReasonAndStatus() {
        ResponseStatusException exception = new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Ce signalement possède déjà cinq pièces jointes."
        );

        ResponseEntity<ApiErrorResponse> response =
                handler.handleResponseStatus(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("Conflict");
        assertThat(response.getBody().message())
                .isEqualTo("Ce signalement possède déjà cinq pièces jointes.");
        assertThat(response.getBody().path()).isEqualTo("/api/employe/tickets");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("handleMaxUploadSize - Retourne 400 Bad Request quand un fichier dépasse la limite")
    void handleMaxUploadSize_shouldReturn400() {
        MaxUploadSizeExceededException exception =
                new MaxUploadSizeExceededException(5L * 1024 * 1024);

        ResponseEntity<ApiErrorResponse> response =
                handler.handleMaxUploadSize(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message())
                .isEqualTo("Fichier vide ou taille supérieure à 5 Mo.");
    }

    @Test
    @DisplayName("handleAccessDenied - Retourne 403 Forbidden")
    void handleAccessDenied_shouldReturn403() {
        AccessDeniedException exception = new AccessDeniedException("Forbidden");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleAccessDenied(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(403);
        assertThat(response.getBody().message()).isEqualTo("Accès refusé.");
    }

    @Test
    @DisplayName("handleUnexpected - Masque les détails techniques et retourne 500")
    void handleUnexpected_shouldMaskInternalDetailsAndReturn500() {
        when(request.getMethod()).thenReturn("POST");
        RuntimeException exception = new RuntimeException("SQL detail leak");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleUnexpected(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().message())
                .isEqualTo("Une erreur interne est survenue.");
    }
}
