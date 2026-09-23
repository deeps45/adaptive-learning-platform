package com.learning.platform.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProgressDtos {
    private ProgressDtos() {}

    public record DueReviewCard(
            UUID questionId, String questionText, UUID quizId, String quizTitle, LocalDate dueDate, int repetitions) {}

    public record StudentProgressResponse(
            int quizzesAttempted,
            double averageScorePercent,
            int cardsDueForReview,
            int cardsMastered, // repetitions >= 3, a simple "mature card" heuristic (cf. Anki's own default)
            List<DueReviewCard> dueReviews) {}
}
