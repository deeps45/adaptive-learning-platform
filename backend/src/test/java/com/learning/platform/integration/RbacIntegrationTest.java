package com.learning.platform.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * These tests exist because a {@code @PreAuthorize("hasRole('INSTRUCTOR')")} annotation only
 * proves a STUDENT-role JWT is rejected - it says nothing about whether one instructor can see or
 * modify *another* instructor's data, which is a data-ownership check, not a role check (see
 * CourseService.requireOwnedCourse). Both failure modes are tested here explicitly rather than
 * assumed from the annotation being present.
 */
class RbacIntegrationTest extends IntegrationTestBase {

    @Test
    void studentCannotCreateACourse() throws Exception {
        String studentToken = registerAndLogin("STUDENT");
        Map<String, Object> body = Map.of("title", "Illegit Course", "description", "x");
        mockMvc.perform(
                        post("/api/courses")
                                .header("Authorization", bearer(studentToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void instructorCannotEnrollInACourse() throws Exception {
        String instructorToken = registerAndLogin("INSTRUCTOR");
        String courseId = createCourse(instructorToken, "Some Course");

        mockMvc.perform(post("/api/courses/" + courseId + "/enroll").header("Authorization", bearer(instructorToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void instructorCannotViewAnotherInstructorsCourseDashboard() throws Exception {
        String instructorA = registerAndLogin("INSTRUCTOR");
        String instructorB = registerAndLogin("INSTRUCTOR");
        String courseId = createCourse(instructorA, "Instructor A's Course");

        // Owner can view their own dashboard.
        mockMvc.perform(get("/api/courses/" + courseId + "/dashboard").header("Authorization", bearer(instructorA)))
                .andExpect(status().isOk());

        // A different instructor, despite holding a perfectly valid INSTRUCTOR-role JWT, cannot.
        mockMvc.perform(get("/api/courses/" + courseId + "/dashboard").header("Authorization", bearer(instructorB)))
                .andExpect(status().isForbidden());
    }

    @Test
    void instructorCannotCreateAQuizInAnotherInstructorsCourse() throws Exception {
        String instructorA = registerAndLogin("INSTRUCTOR");
        String instructorB = registerAndLogin("INSTRUCTOR");
        String courseId = createCourse(instructorA, "A's Course");

        Map<String, Object> quizBody =
                Map.of(
                        "title", "Sneaky Quiz",
                        "description", "x",
                        "questions",
                                List.of(
                                        Map.of(
                                                "text", "Q1",
                                                "choices",
                                                        List.of(
                                                                Map.of("text", "A", "correct", true),
                                                                Map.of("text", "B", "correct", false)))));

        mockMvc.perform(
                        post("/api/courses/" + courseId + "/quizzes")
                                .header("Authorization", bearer(instructorB))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(quizBody)))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotViewQuizzesForACourseTheyAreNotEnrolledIn() throws Exception {
        String instructor = registerAndLogin("INSTRUCTOR");
        String student = registerAndLogin("STUDENT"); // deliberately does not enroll
        String courseId = createCourse(instructor, "Locked Course");
        String quizId = createQuiz(instructor, courseId);

        mockMvc.perform(get("/api/quizzes/" + quizId).header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotSubmitAnotherStudentsAttempt() throws Exception {
        String instructor = registerAndLogin("INSTRUCTOR");
        String studentA = registerAndLogin("STUDENT");
        String studentB = registerAndLogin("STUDENT");
        String courseId = createCourse(instructor, "Shared Course");
        String quizId = createQuiz(instructor, courseId);

        enroll(studentA, courseId);
        enroll(studentB, courseId);

        String attemptId = startAttempt(studentA, quizId);

        // A well-formed, valid submission (not an empty answers list) - the point of this test
        // is that the ownership check rejects it, not that request validation does. An empty
        // list would 400 on @NotEmpty before the service layer's ownership check ever runs,
        // which would make this test pass for the wrong reason.
        JsonNode quiz = getQuiz(studentA, quizId);
        JsonNode q1 = quiz.get("questions").get(0);
        Map<String, Object> submitBody =
                Map.of(
                        "answers",
                        List.of(
                                Map.of(
                                        "questionId", q1.get("id").asText(),
                                        "selectedChoiceId", q1.get("choices").get(0).get("id").asText())));
        mockMvc.perform(
                        post("/api/attempts/" + attemptId + "/submit")
                                .header("Authorization", bearer(studentB))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(submitBody)))
                .andExpect(status().isForbidden());
    }
}
