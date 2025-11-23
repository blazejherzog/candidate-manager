package com.candidate_manager.auth.application;

public class KeycloakUserNotFoundException extends RuntimeException {

    public KeycloakUserNotFoundException(String message) {
        super(message);
    }
}
