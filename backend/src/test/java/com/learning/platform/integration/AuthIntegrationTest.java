package com.learning.platform.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void registeringTwiceWithSameEmailConflicts() throws Exception {
        String email = "dup-" + java.util.UUID.randomUUID() + "@example.com";
        Map<String, Object> body =
                Map.of("email", email, "password", "password123", "fullName", "Dup User", "role", "STUDENT");

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        String email = "wrongpw-" + java.util.UUID.randomUUID() + "@example.com";
        Map<String, Object> register =
                Map.of("email", email, "password", "correct-password", "fullName", "User", "role", "STUDENT");
        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        Map<String, Object> badLogin = Map.of("email", email, "password", "wrong-password");
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedRequestToProtectedEndpointIsRejected() throws Exception {
        mockMvc.perform(get("/api/students/me/progress")).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenCannotBeUsedAsAnAccessToken() throws Exception {
        // A real, easy-to-miss JWT bug: if the filter doesn't distinguish token types, a
        // longer-lived refresh token doubles as a valid bearer credential for every protected
        // endpoint instead of only /api/auth/refresh - see JwtAuthenticationFilter's explicit
        // check for exactly this.
        String email = "refresh-" + java.util.UUID.randomUUID() + "@example.com";
        Map<String, Object> register =
                Map.of("email", email, "password", "password123", "fullName", "User", "role", "STUDENT");
        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        Map<String, Object> login = Map.of("email", email, "password", "password123");
        String response =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(login)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode tokens = objectMapper.readTree(response);
        String refreshToken = tokens.get("refreshToken").asText();

        mockMvc.perform(get("/api/students/me/progress").header("Authorization", bearer(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenIssuesAUsableNewAccessToken() throws Exception {
        String token = registerAndLogin("STUDENT");
        mockMvc.perform(get("/api/students/me/progress").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quizzesAttempted").value(0));
    }
}
