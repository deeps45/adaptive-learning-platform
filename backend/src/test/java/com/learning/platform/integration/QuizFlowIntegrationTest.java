package com.learning.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.learning.platform.entity.ReviewCard;
import com.learning.platform.repository.ReviewCardRepository;
import com.learning.platform.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class QuizFlowIntegrationTest extends IntegrationTestBase {

    @Autowired private ReviewCardRepository reviewCardRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void quizViewNeverExposesWhichChoiceIsCorrect() throws Exception {
        String instructor = registerAndLogin("INSTRUCTOR");
        String student = registerAndLogin("STUDENT");
        String courseId = createCourse(instructor, "Course");
        String quizId = createQuiz(instructor, courseId);
        enroll(student, courseId);

        JsonNode quiz = getQuiz(student, quizId);
        for (JsonNode question : quiz.get("questions")) {
            for (JsonNode choice : question.get("choices")) {
                assertThat(choice.has("correct"))
                        .as("student-facing choice view must not leak the answer key")
                        .isFalse();
            }
        }
    }

    @Test
    void fullQuizTakingFlowGradesCorrectlyAndCreatesReviewCards() throws Exception {
        String instructor = registerAndLogin("INSTRUCTOR");
        String student = registerAndLogin("STUDENT");
        String courseId = createCourse(instructor, "Course");
        String quizId = createQuiz(instructor, courseId);
        enroll(student, courseId);

        JsonNode quiz = getQuiz(student, quizId);
        JsonNode q1 = quiz.get("questions").get(0); // "2 + 2 = ?" -> choice[1] ("4") is correct
        JsonNode q2 = quiz.get("questions").get(1); // "Capital of France?" -> choice[0] ("Paris") is correct

        String attemptId = startAttempt(student, quizId);

        // Answer q1 correctly, q2 incorrectly.
        Map<String, Object> submitBody =
                Map.of(
                        "answers",
                        List.of(
                                Map.of(
                                        "questionId", q1.get("id").asText(),
                                        "selectedChoiceId", q1.get("choices").get(1).get("id").asText()),
                                Map.of(
                                        "questionId", q2.get("id").asText(),
                                        "selectedChoiceId", q2.get("choices").get(1).get("id").asText())));

        mockMvc.perform(
                        post("/api/attempts/" + attemptId + "/submit")
                                .header("Authorization", bearer(student))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(submitBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.percent").value(50.0));

        // Both questions should now have a review card for this student - one on the
        // "answered correctly" schedule, one on the "answered incorrectly" schedule.
        var studentId = userRepository.findByEmail(emailFromToken(student)).orElseThrow().getId();
        ReviewCard correctCard =
                reviewCardRepository
                        .findByStudentIdAndQuestionId(studentId, java.util.UUID.fromString(q1.get("id").asText()))
                        .orElseThrow();
        ReviewCard incorrectCard =
                reviewCardRepository
                        .findByStudentIdAndQuestionId(studentId, java.util.UUID.fromString(q2.get("id").asText()))
                        .orElseThrow();

        assertThat(correctCard.getRepetitions()).isEqualTo(1);
        assertThat(correctCard.getEasinessFactor()).isGreaterThan(2.5); // correct answers raise easiness
        assertThat(incorrectCard.getRepetitions()).isZero();
        assertThat(incorrectCard.getEasinessFactor()).isLessThan(2.5); // incorrect answers lower it

        // Progress endpoint reflects the submitted attempt.
        mockMvc.perform(get("/api/students/me/progress").header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quizzesAttempted").value(1))
                .andExpect(jsonPath("$.averageScorePercent").value(50.0));
    }

    @Test
    void leavingAQuestionUnansweredGradesItAsIncorrectRatherThanSkippingIt() throws Exception {
        String instructor = registerAndLogin("INSTRUCTOR");
        String student = registerAndLogin("STUDENT");
        String courseId = createCourse(instructor, "Course");
        String quizId = createQuiz(instructor, courseId); // 2 questions
        enroll(student, courseId);

        JsonNode quiz = getQuiz(student, quizId);
        JsonNode q1 = quiz.get("questions").get(0);
        // q2 is deliberately left out of the submission entirely.
        String attemptId = startAttempt(student, quizId);

        Map<String, Object> submitBody =
                Map.of(
                        "answers",
                        List.of(
                                Map.of(
                                        "questionId", q1.get("id").asText(),
                                        "selectedChoiceId", q1.get("choices").get(1).get("id").asText())));

        mockMvc.perform(
                        post("/api/attempts/" + attemptId + "/submit")
                                .header("Authorization", bearer(student))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(submitBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.score").value(1)) // q1 correct, q2 unanswered -> not counted as correct
                .andExpect(jsonPath("$.answers.length()").value(2)) // one result per quiz question, not per submitted answer
                .andExpect(jsonPath("$.answers[1].correct").value(false))
                .andExpect(jsonPath("$.answers[1].selectedChoiceId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void reSubmittingAnAlreadyGradedAttemptReturnsTheSameResultWithoutRegrading() throws Exception {
        String instructor = registerAndLogin("INSTRUCTOR");
        String student = registerAndLogin("STUDENT");
        String courseId = createCourse(instructor, "Course");
        String quizId = createQuiz(instructor, courseId);
        enroll(student, courseId);

        JsonNode quiz = getQuiz(student, quizId);
        JsonNode q1 = quiz.get("questions").get(0);
        String attemptId = startAttempt(student, quizId);

        Map<String, Object> submitBody =
                Map.of(
                        "answers",
                        List.of(
                                Map.of(
                                        "questionId", q1.get("id").asText(),
                                        "selectedChoiceId", q1.get("choices").get(1).get("id").asText())));

        String firstResponse =
                mockMvc.perform(
                                post("/api/attempts/" + attemptId + "/submit")
                                        .header("Authorization", bearer(student))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(submitBody)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // Submit again - same attempt, same (or even different) answers.
        String secondResponse =
                mockMvc.perform(
                                post("/api/attempts/" + attemptId + "/submit")
                                        .header("Authorization", bearer(student))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(submitBody)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(secondResponse).isEqualTo(firstResponse);

        // And the review card was only advanced once, not twice.
        var studentId = userRepository.findByEmail(emailFromToken(student)).orElseThrow().getId();
        ReviewCard card =
                reviewCardRepository
                        .findByStudentIdAndQuestionId(studentId, java.util.UUID.fromString(q1.get("id").asText()))
                        .orElseThrow();
        assertThat(card.getRepetitions()).isEqualTo(1); // would be 2 if the resubmit re-graded
    }

    @Test
    void dueReviewsSurfaceThroughTheProgressEndpoint() throws Exception {
        String instructor = registerAndLogin("INSTRUCTOR");
        String student = registerAndLogin("STUDENT");
        String courseId = createCourse(instructor, "Course");
        String quizId = createQuiz(instructor, courseId);
        enroll(student, courseId);

        JsonNode quiz = getQuiz(student, quizId);
        JsonNode q1 = quiz.get("questions").get(0);
        String attemptId = startAttempt(student, quizId);
        Map<String, Object> submitBody =
                Map.of(
                        "answers",
                        List.of(
                                Map.of(
                                        "questionId", q1.get("id").asText(),
                                        "selectedChoiceId", q1.get("choices").get(1).get("id").asText())));
        mockMvc.perform(
                        post("/api/attempts/" + attemptId + "/submit")
                                .header("Authorization", bearer(student))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(submitBody)))
                .andExpect(status().isOk());

        // The card SM-2 just scheduled is due tomorrow, not today (see
        // SpacedRepetitionServiceTest for the algorithm itself) - backdating it here tests the
        // due-reviews *surfacing* path (repository query -> service -> controller -> JSON), not
        // the scheduling math, which already has its own dedicated unit tests.
        var studentId = userRepository.findByEmail(emailFromToken(student)).orElseThrow().getId();
        ReviewCard card =
                reviewCardRepository
                        .findByStudentIdAndQuestionId(studentId, java.util.UUID.fromString(q1.get("id").asText()))
                        .orElseThrow();
        card.setDueDate(LocalDate.now());
        reviewCardRepository.save(card);

        mockMvc.perform(get("/api/students/me/progress").header("Authorization", bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardsDueForReview").value(1))
                .andExpect(jsonPath("$.dueReviews[0].questionId").value(q1.get("id").asText()));
    }
}
