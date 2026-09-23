package com.learning.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.learning.platform.entity.QuizAttempt;
import com.learning.platform.repository.QuizAttemptRepository;
import com.learning.platform.repository.ReviewCardRepository;
import com.learning.platform.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Proves QuizAttemptService.submit()'s double-submission protection under an actual race, not
 * just by reading the code and trusting the {@code SELECT ... FOR UPDATE} annotation. Fires two
 * real concurrent HTTP-level submissions for the same attempt (simulating, e.g., a client retry
 * after a slow response, or a double-click) and asserts exactly one grading took effect.
 *
 * <p>{@code @RepeatedTest} because a race condition that doesn't reproduce on every run isn't
 * disproven by one green run - both threads are released from a shared {@link CountDownLatch}
 * to make the race window as tight as this test framework can make it, and repeating it makes a
 * flaky win in the "both threads graded it" direction much less likely to hide behind luck.
 */
class QuizConcurrencyIntegrationTest extends IntegrationTestBase {

    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private ReviewCardRepository reviewCardRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private com.learning.platform.repository.AttemptAnswerRepository attemptAnswerRepository;

    @RepeatedTest(5)
    void concurrentSubmissionsOfTheSameAttemptGradeExactlyOnce() throws Exception {
        String instructor = registerAndLogin("INSTRUCTOR");
        String student = registerAndLogin("STUDENT");
        String courseId = createCourse(instructor, "Concurrency Course");
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
        String submitJson = objectMapper.writeValueAsString(submitBody);

        int concurrentRequests = 8;
        ExecutorService pool = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger httpFailures = new AtomicInteger(0);

        try {
            List<java.util.concurrent.Future<Void>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < concurrentRequests; i++) {
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    try {
                                        go.await();
                                        mockMvc.perform(
                                                        post("/api/attempts/" + attemptId + "/submit")
                                                                .header("Authorization", bearer(student))
                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                .content(submitJson))
                                                .andExpect(
                                                        org.springframework.test.web.servlet.result
                                                                .MockMvcResultMatchers.status()
                                                                .isOk());
                                    } catch (Exception e) {
                                        httpFailures.incrementAndGet();
                                        throw new RuntimeException(e);
                                    }
                                    return null;
                                }));
            }

            ready.await(5, TimeUnit.SECONDS);
            go.countDown(); // release all threads at once - the actual race
            for (var f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(httpFailures.get())
                .as("every concurrent submission should succeed (200) - idempotent, not an error")
                .isZero();

        UUID attemptUuid = UUID.fromString(attemptId);
        QuizAttempt finalAttempt = attemptRepository.findById(attemptUuid).orElseThrow();
        assertThat(finalAttempt.getScore()).isEqualTo(1);
        // The quiz createQuiz() builds has 2 questions; this test only submits an answer for the
        // first, so exactly 2 AttemptAnswer rows is correct grading (one per question in the
        // quiz - the second recorded as an unanswered/incorrect answer, see the comment in
        // QuizAttemptService.submit()), not evidence of double-grading. Double-grading would
        // show as 4 (2 questions x 2 racing submissions winning), not 2 - that's the number this
        // assertion is actually here to rule out.
        //
        // Counted via a query, not finalAttempt.getAnswers().size() - that collection is a lazy
        // proxy and this test method isn't @Transactional (deliberately: an outer transaction
        // here would share a connection across the concurrent submissions below and mask the
        // exact race this test exists to exercise), so touching it directly would just be a
        // LazyInitializationException, not a meaningful assertion either way.
        assertThat(attemptAnswerRepository.countByAttemptId(attemptUuid))
                .as("2 answer rows (one per quiz question) - not 4, which would mean two racing submissions both graded")
                .isEqualTo(2);

        var studentId = userRepository.findByEmail(emailFromToken(student)).orElseThrow().getId();
        var card =
                reviewCardRepository
                        .findByStudentIdAndQuestionId(studentId, UUID.fromString(q1.get("id").asText()))
                        .orElseThrow();
        assertThat(card.getRepetitions())
                .as("SM-2 state must advance exactly once per submitted attempt, not once per racing request")
                .isEqualTo(1);
    }
}
