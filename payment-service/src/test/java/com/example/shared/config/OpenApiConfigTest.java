package com.example.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void openApiBeanDescribesService() {
        OpenAPI api = new OpenApiConfig().openAPI();
        assertThat(api.getInfo().getTitle()).isEqualTo("Payment Service API");
        assertThat(api.getInfo().getDescription())
                .isEqualTo("Contract-scoped payments with idempotent creation and import coordination");
        assertThat(api.getInfo().getVersion()).isEqualTo("v1");
    }
}
