package com.panol_project.backendpanol.modules.email.domain;

public interface EmailDeliveryPort {

    String send(String recipientEmail, String subject, String html);
}
