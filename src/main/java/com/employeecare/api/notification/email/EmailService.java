package com.employeecare.api.notification.email;

import jakarta.validation.constraints.Email;

public interface EmailService {
    void sendActivationEmail
            (String recipientEmail,
             String recipientName,
             String activationLink);
}