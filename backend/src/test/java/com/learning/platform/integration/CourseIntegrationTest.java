package com.learning.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for two real LazyInitializationException bugs found by manually running
 * this app in a browser rather than by the (fully passing) test suite - see README "Bugs found by
 * actually running this". GET /api/courses and GET /api/courses/{id}/quizzes both threw outside
 * the Hibernate session because the association read by their JSON serialization wasn't eagerly
 * fetched. Fixed via CourseRepository.findAllWithInstructor() and
 * QuizRepository.findByCourseIdWithQuestionsAndChoices() respectively. Both bugs slipped past the
 * full suite at the time because nothing asserted on the *content* of these two endpoints' JSON,
 * only that the individual create/enroll/submit calls behind them succeeded.
 */
class CourseIntegrationTest extends IntegrationTestBase {

    @Test
    void listingCoursesPopulatesInstructorNameWithoutLazyInitializationException() throws Exception {
        String instructor = registerAndLogin("INSTRUCTOR");
        String title = "Intro to Testing " + UUID.randomUUID();
        createCourse(instructor, title);

        String response =
                mockMvc.perform(get("/api/courses").header("Authorization", bearer(instructor)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode courses = objectMapper.readTree(response);
        JsonNode course =
                findByTitle(courses, title)
                        .orElseThrow(() -> new AssertionError("course not found in list response"));
        assertThat(course.get("instructorName").asText()).isEqualTo("Test User");
        assertThat(course.hasNonNull("instructorId")).isTrue();
    }

    @Test
    void listingQuizzesForACoursePopulatesQuestionsAndChoicesWithoutLazyInitializationException()
            throws Exception {
        String instructor = registerAndLogin("INSTRUCTOR");
        String courseId = createCourse(instructor, "Course");
        createQuiz(instructor, courseId); // always 2 questions, 2 choices each - see createQuiz()

        String response =
                mockMvc.perform(get("/api/courses/" + courseId + "/quizzes").header("Authorization", bearer(instructor)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode quizzes = objectMapper.readTree(response);
        assertThat(quizzes).hasSize(1);
        JsonNode questions = quizzes.get(0).get("questions");
        assertThat(questions).hasSize(2);
        for (JsonNode question : questions) {
            assertThat(question.get("choices")).isNotEmpty();
        }
    }

    private java.util.Optional<JsonNode> findByTitle(JsonNode courses, String title) {
        for (JsonNode course : courses) {
            if (course.get("title").asText().equals(title)) {
                return java.util.Optional.of(course);
            }
        }
        return java.util.Optional.empty();
    }
}
