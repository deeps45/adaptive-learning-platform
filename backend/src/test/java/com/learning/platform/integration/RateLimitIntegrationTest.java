package com.learning.platform.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Exercises RateLimitFilter's actual throttling behavior against the real production default
 * (10/min, see application.yml's app.rate-limit.auth-requests-per-minute) - safe to rely on that
 * exact number here because IntegrationTestBase clears the shared filter's bucket before every
 * test method.
 */
class RateLimitIntegrationTest extends IntegrationTestBase {

    @Test
    void exceedingTheAuthRateLimitReturns429() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", "nobody@example.com", "password", "wrong"));

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }

        // The 11th request within the same minute, from the same (synthetic MockMvc) client IP,
        // should be throttled rather than evaluated against the credentials at all.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }
}
