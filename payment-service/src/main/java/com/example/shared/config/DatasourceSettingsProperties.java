package com.example.shared.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds the same {@code spring.datasource.*} keys as Spring Boot. Startup fails fast if
 * {@link #getUrl()} or {@link #getUsername()} is blank, avoiding silent use of a wrong profile.
 */
@ConfigurationProperties(prefix = "spring.datasource")
@Getter
@Setter
@Validated
public class DatasourceSettingsProperties {

    @NotBlank(message = "spring.datasource.url must be set and non-blank")
    private String url;

    @NotBlank(message = "spring.datasource.username must be set and non-blank")
    private String username;
}
