package com.panol_project.backendpanol.shared.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
