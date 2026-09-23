package com.learning.platform.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.security.RateLimitFilter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Every integration test in this project runs against a real, ephemeral Postgres in a Docker
 * container - not H2 in-memory. H2's SQL dialect and locking semantics differ from Postgres's in
 * ways that matter directly to this codebase: the pessimistic {@code SELECT ... FOR UPDATE} lock
 * QuizAttemptService.submit() relies on for double-submission protection behaves differently (or
 * isn't meaningfully testable at all) against H2's in-memory engine. Testing against what
 * production actually runs is the point, not a formality.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
public abstract class IntegrationTestBase {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("learning_platform_test")
                    .withUsername("test")
                    .withPassword("test");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired private RateLimitFilter rateLimitFilter;

    /** All tests in this suite share one Spring context (and therefore one RateLimitFilter
     * instance) across many classes, all hitting register/login through MockMvc's fixed
     * "127.0.0.1" remote address - clear its state before every test so unrelated tests don't
     * exhaust each other's per-IP bucket. See RateLimitIntegrationTest for the class that actually
     * exercises the limit itself. */
    @BeforeEach
    void resetRateLimiter() {
        rateLimitFilter.resetForTests();
    }

    /** Registers a fresh user (unique email per call) and returns their access token. */
    protected String registerAndLogin(String role) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        Map<String, Object> registerBody =
                Map.of(
                        "email", email,
                        "password", "password123",
                        "fullName", "Test User",
                        "role", role);
        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerBody)))
                .andExpect(status().isCreated());

        Map<String, Object> loginBody = Map.of("email", email, "password", "password123");
        String response =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(loginBody)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    // --- shared fixtures, used across every integration test class below ---

    protected String createCourse(String instructorToken, String title) throws Exception {
        Map<String, Object> body = Map.of("title", title, "description", "desc");
        String response =
                mockMvc.perform(
                                post("/api/courses")
                                        .header("Authorization", bearer(instructorToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(body)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    /** Always builds a 2-question quiz - several tests rely on that exact shape (e.g. leaving one
     * question unanswered on purpose), so changing it here would ripple across the suite. */
    protected String createQuiz(String instructorToken, String courseId) throws Exception {
        Map<String, Object> quizBody =
                Map.of(
                        "title", "Quiz 1",
                        "description", "desc",
                        "questions",
                                List.of(
                                        Map.of(
                                                "text", "2 + 2 = ?",
                                                "choices",
                                                        List.of(
                                                                Map.of("text", "3", "correct", false),
                                                                Map.of("text", "4", "correct", true))),
                                        Map.of(
                                                "text", "Capital of France?",
                                                "choices",
                                                        List.of(
                                                                Map.of("text", "Paris", "correct", true),
                                                                Map.of("text", "Rome", "correct", false)))));
        String response =
                mockMvc.perform(
                                post("/api/courses/" + courseId + "/quizzes")
                                        .header("Authorization", bearer(instructorToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(quizBody)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    protected void enroll(String studentToken, String courseId) throws Exception {
        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").header("Authorization", bearer(studentToken)))
                .andExpect(status().isCreated());
    }

    protected String startAttempt(String studentToken, String quizId) throws Exception {
        String response =
                mockMvc.perform(
                                post("/api/quizzes/" + quizId + "/attempts")
                                        .header("Authorization", bearer(studentToken)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(response).get("attemptId").asText();
    }

    protected JsonNode getQuiz(String token, String quizId) throws Exception {
        String response =
                mockMvc.perform(get("/api/quizzes/" + quizId).header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(response);
    }

    /** registerAndLogin() doesn't return the email it generated, and nothing else in these
     * responses exposes a user id directly - decoding it straight out of the JWT payload is
     * simpler than threading an extra return value through every helper just for tests. */
    protected String emailFromToken(String token) {
        String payload = token.split("\\.")[1];
        String json = new String(java.util.Base64.getUrlDecoder().decode(payload));
        try {
            return objectMapper.readTree(json).get("email").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
