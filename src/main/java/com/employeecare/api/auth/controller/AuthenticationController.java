package com.employeecare.api.auth.controller;

import com.employeecare.api.auth.service.AuthenticationService;

import com.employeecare.api.generated.apis.AuthentificationApi;

import com.employeecare.api.generated.models.LoginRequest;
import com.employeecare.api.generated.models.LoginSuccessResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthenticationController implements AuthentificationApi {

    private final AuthenticationService authenticationService;

    public AuthenticationController(
            AuthenticationService authenticationService
    ) {
        this.authenticationService = authenticationService;
    }

    @Override
    public ResponseEntity<LoginSuccessResponse> loginUser(
            LoginRequest loginRequest
    ) {

        LoginSuccessResponse response =
                authenticationService.login(loginRequest);

        return ResponseEntity.ok(response);
    }
}