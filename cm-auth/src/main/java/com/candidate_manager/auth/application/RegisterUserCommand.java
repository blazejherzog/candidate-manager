package com.candidate_manager.auth.application;

public record RegisterUserCommand(
        String username,
        String email,
        String password,
        String firstName,
        String lastName) {

    @Override
    public String toString() {
        return "RegisterUserCommand[username=%s, email=%s, password=***, firstName=%s, lastName=%s]"
                .formatted(username, email, firstName, lastName);
    }
}
