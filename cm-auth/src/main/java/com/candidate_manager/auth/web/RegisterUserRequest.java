package com.candidate_manager.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterUserRequest {

    @NotBlank
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

    @NotBlank
    private String role;
}
