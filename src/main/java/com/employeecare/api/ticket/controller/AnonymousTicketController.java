package com.employeecare.api.ticket.controller;

import com.employeecare.api.generated.apis.PublicApi;
import com.employeecare.api.generated.models.AnonymousTicketCreatedResponse;
import com.employeecare.api.generated.models.CreateAnonymousTicketRequest;
import com.employeecare.api.ticket.service.AnonymousTicketService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnonymousTicketController implements PublicApi {

    private final AnonymousTicketService anonymousTicketService;

    public AnonymousTicketController(
            AnonymousTicketService anonymousTicketService
    ) {
        this.anonymousTicketService = anonymousTicketService;
    }

    @Override
    public ResponseEntity<AnonymousTicketCreatedResponse> createAnonymousTicket(
            @Valid @RequestBody CreateAnonymousTicketRequest request
    ) {

        AnonymousTicketCreatedResponse response =
                anonymousTicketService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
