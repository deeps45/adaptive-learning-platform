package com.learning.platform.service;

import com.learning.platform.dto.QuizDtos.AnswerResult;
import com.learning.platform.dto.QuizDtos.AttemptResult;
import com.learning.platform.dto.QuizDtos.SubmitAnswerRequest;
import com.learning.platform.dto.QuizDtos.SubmitAttemptRequest;
import com.learning.platform.entity.AttemptAnswer;
import com.learning.platform.entity.AttemptStatus;
import com.learning.platform.entity.Choice;
import com.learning.platform.entity.Question;
import com.learning.platform.entity.Quiz;
import com.learning.platform.entity.QuizAttempt;
import com.learning.platform.entity.ReviewCard;
import com.learning.platform.entity.User;
import com.learning.platform.exception.ForbiddenException;
import com.learning.platform.exception.NotFoundException;
import com.learning.platform.repository.QuizAttemptRepository;
import com.learning.platform.repository.ReviewCardRepository;
import com.learning.platform.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizAttemptService {

    private final QuizAttemptRepository attemptRepository;
    private final ReviewCardRepository reviewCardRepository;
    private final UserRepository userRepository;
    private final QuizService quizService;
    private final SpacedRepetitionService spacedRepetitionService;

    public QuizAttemptService(
            QuizAttemptRepository attemptRepository,
            ReviewCardRepository reviewCardRepository,
            UserRepository userRepository,
            QuizService quizService,
            SpacedRepetitionService spacedRepetitionService) {
        this.attemptRepository = attemptRepository;
        this.reviewCardRepository = reviewCardRepository;
        this.userRepository = userRepository;
        this.quizService = quizService;
        this.spacedRepetitionService = spacedRepetitionService;
    }

    @Transactional
    public QuizAttempt startAttempt(UUID studentId, UUID quizId) {
        Quiz quiz = quizService.getQuizEntity(quizId);
        quizService.requireCanAccessQuiz(studentId, "STUDENT", quiz);
        User student = userRepository.findById(studentId).orElseThrow(() -> new NotFoundException("User not found"));

        QuizAttempt attempt =
                QuizAttempt.builder()
                        .student(student)
                        .quiz(quiz)
                        .status(AttemptStatus.IN_PROGRESS)
                        .totalQuestions(quiz.getQuestions().size())
                        .build();
        return attemptRepository.save(attempt);
    }

    /**
     * Grades a submitted attempt and updates every answered question's spaced-repetition state.
     *
     * <p>Double-submission protection, layered rather than single-mechanism:
     *
     * <ol>
     *   <li>{@code findByIdForUpdate} takes a row-level {@code SELECT ... FOR UPDATE} lock, so
     *       two concurrent submissions for the *same* attempt serialize at the database instead
     *       of both reading {@code IN_PROGRESS} and both grading it.
     *   <li>Once the first transaction commits (status now {@code SUBMITTED}), the second
     *       transaction's lock is granted, it re-reads the now-{@code SUBMITTED} row, and returns
     *       the *existing* result instead of re-grading - submitting twice is idempotent, not an
     *       error and not a double-graded attempt.
     *   <li>{@code @Version} on the entity is a second, independent check: even if the locking
     *       above were ever weakened, a stale in-memory copy being saved raises
     *       {@code OptimisticLockException} rather than silently overwriting a concurrent write.
     * </ol>
     *
     * See QuizConcurrencyIntegrationTest for the test that actually fires two submissions at once
     * and verifies exactly one grading happened.
     */
    @Transactional
    public AttemptResult submit(UUID studentId, UUID attemptId, SubmitAttemptRequest request) {
        QuizAttempt attempt =
                attemptRepository.findByIdForUpdate(attemptId).orElseThrow(() -> new NotFoundException("Attempt not found"));

        if (!attempt.getStudent().getId().equals(studentId)) {
            throw new ForbiddenException("This is not your attempt");
        }

        if (attempt.getStatus() == AttemptStatus.SUBMITTED) {
            return toResult(attempt); // already graded - return the existing result, don't re-grade
        }

        Map<UUID, SubmitAnswerRequest> byQuestion =
                request.answers().stream()
                        .collect(Collectors.toMap(SubmitAnswerRequest::questionId, a -> a));

        java.util.Set<Question> questions = attempt.getQuiz().getQuestions();
        int correctCount = 0;
        Instant now = Instant.now();

        // Iterates every question in the quiz, not just the ones present in the request: a
        // question the student never answered still gets an AttemptAnswer (selectedChoice=null,
        // correct=false) and its own SM-2 review card update via updateReviewCard() below - an
        // unanswered question is graded as incorrect and scheduled for review like any other
        // wrong answer, not silently skipped. That's what makes AttemptAnswer rows count equal
        // to the quiz's total question count, not the number of answers actually submitted - see
        // QuizConcurrencyIntegrationTest, which initially asserted the wrong number here before
        // this comment existed to explain why.
        for (Question question : questions) {
            SubmitAnswerRequest submitted = byQuestion.get(question.getId());
            Choice selected =
                    submitted != null && submitted.selectedChoiceId() != null
                            ? question.getChoices().stream()
                                    .filter(c -> c.getId().equals(submitted.selectedChoiceId()))
                                    .findFirst()
                                    .orElse(null)
                            : null;
            boolean correct = selected != null && selected.isCorrect();
            if (correct) correctCount++;

            attempt.getAnswers().add(
                    AttemptAnswer.builder()
                            .attempt(attempt)
                            .question(question)
                            .selectedChoice(selected)
                            .correct(correct)
                            .build());

            updateReviewCard(attempt.getStudent(), question, correct, now);
        }

        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(now);
        attempt.setScore(correctCount);
        attemptRepository.save(attempt);

        return toResult(attempt);
    }

    private void updateReviewCard(User student, Question question, boolean correct, Instant now) {
        ReviewCard card =
                reviewCardRepository
                        .findByStudentIdAndQuestionId(student.getId(), question.getId())
                        .orElseGet(() -> ReviewCard.builder().student(student).question(question).build());
        spacedRepetitionService.review(card, correct, now);
        reviewCardRepository.save(card);
    }

    private AttemptResult toResult(QuizAttempt attempt) {
        List<AnswerResult> answers =
                attempt.getAnswers().stream()
                        .map(
                                a -> {
                                    UUID correctChoiceId =
                                            a.getQuestion().getChoices().stream()
                                                    .filter(Choice::isCorrect)
                                                    .map(Choice::getId)
                                                    .findFirst()
                                                    .orElse(null);
                                    return new AnswerResult(
                                            a.getQuestion().getId(),
                                            a.getSelectedChoice() != null ? a.getSelectedChoice().getId() : null,
                                            correctChoiceId,
                                            a.isCorrect());
                                })
                        .toList();
        double percent = attempt.getTotalQuestions() == 0 ? 0.0 : 100.0 * attempt.getScore() / attempt.getTotalQuestions();
        return new AttemptResult(attempt.getId(), attempt.getScore(), attempt.getTotalQuestions(), percent, answers);
    }
}
