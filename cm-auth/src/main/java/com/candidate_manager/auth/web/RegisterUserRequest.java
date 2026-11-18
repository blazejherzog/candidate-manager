package com.candidate_manager.auth.web;

import com.candidate_manager.auth.application.RegisterUserCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank @Size(max = 255) String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 255) String firstName,
        @NotBlank @Size(max = 255) String lastName) {

    RegisterUserCommand toCommand() {
        return new RegisterUserCommand(username, email, password, firstName, lastName);
    }

    @Override
    public String toString() {
        return "RegisterUserRequest[username=%s, email=%s, password=***, firstName=%s, lastName=%s]"
                .formatted(username, email, firstName, lastName);
    }
}
