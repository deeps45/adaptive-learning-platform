package com.learning.platform.service;

import com.learning.platform.dto.ProgressDtos.DueReviewCard;
import com.learning.platform.dto.ProgressDtos.StudentProgressResponse;
import com.learning.platform.entity.AttemptStatus;
import com.learning.platform.entity.QuizAttempt;
import com.learning.platform.entity.ReviewCard;
import com.learning.platform.repository.QuizAttemptRepository;
import com.learning.platform.repository.ReviewCardRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressService {

    // A card with 3+ consecutive correct reviews is considered "mature" - the same rough
    // threshold Anki's own default scheduler uses to distinguish "learning" from "mature" cards.
    private static final int MATURE_REPETITIONS_THRESHOLD = 3;

    private final QuizAttemptRepository attemptRepository;
    private final ReviewCardRepository reviewCardRepository;

    public ProgressService(QuizAttemptRepository attemptRepository, ReviewCardRepository reviewCardRepository) {
        this.attemptRepository = attemptRepository;
        this.reviewCardRepository = reviewCardRepository;
    }

    @Transactional(readOnly = true)
    public StudentProgressResponse getProgress(UUID studentId) {
        List<QuizAttempt> submitted =
                attemptRepository.findByStudentId(studentId).stream()
                        .filter(a -> a.getStatus() == AttemptStatus.SUBMITTED)
                        .toList();

        double averageScore =
                submitted.isEmpty()
                        ? 0.0
                        : submitted.stream()
                                .mapToDouble(a -> 100.0 * a.getScore() / a.getTotalQuestions())
                                .average()
                                .orElse(0.0);

        List<ReviewCard> allCards = reviewCardRepository.findByStudentId(studentId);
        int mastered = (int) allCards.stream().filter(c -> c.getRepetitions() >= MATURE_REPETITIONS_THRESHOLD).count();

        List<ReviewCard> due =
                reviewCardRepository.findByStudentIdAndDueDateLessThanEqualOrderByDueDateAsc(
                        studentId, LocalDate.now());

        List<DueReviewCard> dueViews =
                due.stream()
                        .map(
                                c ->
                                        new DueReviewCard(
                                                c.getQuestion().getId(),
                                                c.getQuestion().getText(),
                                                c.getQuestion().getQuiz().getId(),
                                                c.getQuestion().getQuiz().getTitle(),
                                                c.getDueDate(),
                                                c.getRepetitions()))
                        .toList();

        return new StudentProgressResponse(submitted.size(), averageScore, due.size(), mastered, dueViews);
    }
}
