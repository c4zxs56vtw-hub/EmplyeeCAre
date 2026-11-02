package com.employeecare.api.auth.controller;

import com.employeecare.api.generated.models.AuthErrorCode;
import com.employeecare.api.generated.models.AuthErrorResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        assignableTypes = AuthenticationController.class
)
public class AuthenticationExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<AuthErrorResponse> handleBadCredentials(
            BadCredentialsException exception
    ) {

        return buildError(
                HttpStatus.UNAUTHORIZED,
                AuthErrorCode.INVALID_CREDENTIALS,
                "Adresse email ou mot de passe incorrect."
        );
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<AuthErrorResponse> handleDisabledAccount(
            DisabledException exception
    ) {

        return buildError(
                HttpStatus.FORBIDDEN,
                AuthErrorCode.ACCOUNT_DEACTIVATED,
                "Ce compte a été désactivé. Contactez l'administrateur."
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AuthErrorResponse> handleInvalidRequest(
            IllegalArgumentException exception
    ) {

        String message = (exception.getMessage() != null && !exception.getMessage().isBlank())
                ? exception.getMessage()
                : "Veuillez renseigner votre adresse email et votre mot de passe.";

        return buildError(
                HttpStatus.BAD_REQUEST,
                AuthErrorCode.EMPTY_FIELDS,
                message
        );
    }

    private ResponseEntity<AuthErrorResponse> buildError(
            HttpStatus status,
            AuthErrorCode errorCode,
            String message
    ) {

        AuthErrorResponse response = new AuthErrorResponse();

        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setErrorMessage(message);

        return ResponseEntity
                .status(status)
                .body(response);
    }
}
