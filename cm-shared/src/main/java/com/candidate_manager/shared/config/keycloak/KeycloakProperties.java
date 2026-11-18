package com.candidate_manager.shared.config.keycloak;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {

    @NotBlank
    private String url;
    @NotBlank
    private String realm;
    @NotBlank
    private String clientId;
    @NotBlank
    private String clientSecret;
}
