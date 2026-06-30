package com.panol_project.backendpanol.modules.email.domain;

public enum EmailOutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
