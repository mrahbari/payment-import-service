package com.example.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimitPropertiesTest {

    @Test
    void defaults() {
        RateLimitProperties p = new RateLimitProperties();
        assertThat(p.isEnabled()).isTrue();
        assertThat(p.getCapacity()).isEqualTo(120L);
        assertThat(p.getRefillPerMinute()).isEqualTo(120L);
    }

    @Test
    void setters() {
        RateLimitProperties p = new RateLimitProperties();
        p.setEnabled(false);
        p.setCapacity(10);
        p.setRefillPerMinute(5);
        assertThat(p.isEnabled()).isFalse();
        assertThat(p.getCapacity()).isEqualTo(10L);
        assertThat(p.getRefillPerMinute()).isEqualTo(5L);
    }
}
