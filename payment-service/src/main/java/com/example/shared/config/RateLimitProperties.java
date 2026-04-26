package com.example.shared.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.rate-limit")
@Getter
@Setter
@Validated
public class RateLimitProperties {

    private boolean enabled = true;

    @Min(1)
    private long capacity = 120;

    @Min(1)
    private long refillPerMinute = 120;
}
