package com.example.payment.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shared.config.WebMvcConfig;
import com.example.payment.web.rest.RootController;
import com.example.payment.web.filter.CorrelationIdFilter;
import com.example.payment.web.filter.RateLimitingFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Rate limiting is off in default {@code application-test.yml} so normal controller tests are not
 * throttled. This narrow slice enables the filter with a capacity of 1 to assert 429 behavior.
 */
@WebMvcTest(controllers = RootController.class)
@AutoConfigureJson
@Import({WebMvcConfig.class, CorrelationIdFilter.class, RateLimitingFilter.class})
@TestPropertySource(
        properties = {
            "app.rate-limit.enabled=true",
            "app.rate-limit.capacity=1",
            "app.rate-limit.refill-per-minute=1"
        })
class RateLimitingFilterWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void secondRequestWithinWindowIsRateLimited() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(containsString("RATE_LIMITED")))
                .andExpect(content().string(containsString("timestamp")));
    }
}
