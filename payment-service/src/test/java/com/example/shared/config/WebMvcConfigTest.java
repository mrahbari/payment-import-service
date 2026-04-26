package com.example.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

class WebMvcConfigTest {

    @Test
    void isWebMvcConfigurer() {
        assertThat(new WebMvcConfig()).isInstanceOf(WebMvcConfigurer.class);
    }
}
