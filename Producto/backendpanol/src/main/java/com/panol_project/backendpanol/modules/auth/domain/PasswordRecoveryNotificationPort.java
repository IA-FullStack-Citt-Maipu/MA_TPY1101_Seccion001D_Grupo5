package com.panol_project.backendpanol.modules.auth.domain;

public interface PasswordRecoveryNotificationPort {
    boolean isAvailable();

    void enqueuePasswordRecoveryEmail(String recipientName, String recipientEmail, String rut, String verificationCode, int expiresInMinutes);
}
