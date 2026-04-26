package com.example.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ErrorResponseTest {

    @Test
    void recordFields() {
        Instant now = Instant.parse("2024-01-15T10:00:00Z");
        ErrorResponse r = new ErrorResponse(now, "CODE", "msg", "/api", "req-1");
        assertThat(r.timestamp()).isEqualTo(now);
        assertThat(r.code()).isEqualTo("CODE");
        assertThat(r.message()).isEqualTo("msg");
        assertThat(r.path()).isEqualTo("/api");
        assertThat(r.requestId()).isEqualTo("req-1");
    }
}
