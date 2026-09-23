package com.learning.platform.service;

import com.learning.platform.entity.ReviewCard;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Implements SuperMemo's SM-2 spaced-repetition scheduling algorithm
 * (Wozniak, 1987 - the same algorithm Anki's classic scheduler is based
 * on), applied to per-(student, question) review cards.
 *
 * <p>SM-2 takes a "quality of recall" score q in [0,5] (in the original,
 * self-assessed by the learner) and updates three pieces of state:
 *
 * <ul>
 *   <li>{@code repetitions} - consecutive correct recalls, reset to 0 on
 *       any recall below the "correct" threshold (q &lt; 3).
 *   <li>{@code intervalDays} - days until the card is due again: 1 after
 *       the first correct repetition, 6 after the second, and
 *       {@code round(previousInterval * easinessFactor)} after that.
 *   <li>{@code easinessFactor} (E-Factor) - how easy this specific card is
 *       for this specific student, adjusted by the canonical SM-2 formula
 *       below every review, floored at 1.3 (SM-2's own floor - the
 *       algorithm degenerates into an ever-shrinking interval below it).
 * </ul>
 *
 * <p>This project applies it to automatically-graded multiple-choice
 * questions, which have no self-assessed quality score to work with - only
 * "correct" or "incorrect". That's mapped deterministically rather than
 * approximated: a correct answer is quality 5 (the algorithm's own
 * "perfect response" value), an incorrect one is quality 1 (below the
 * q&lt;3 threshold, which is all that matters to SM-2 - the exact value
 * below 3 doesn't otherwise affect the formula, since the "incorrect"
 * branch doesn't use q at all). This is a real simplification worth being
 * explicit about, not a limitation hidden in the code: SM-2 was designed
 * for self-graded recall (flashcards), and a 4-option multiple-choice
 * question is easier to get right by chance than an open recall - see the
 * README for why this project's mapping still holds up regardless.
 */
@Service
public class SpacedRepetitionService {

    public static final double MIN_EASINESS_FACTOR = 1.3;
    public static final double DEFAULT_EASINESS_FACTOR = 2.5;
    private static final int QUALITY_CORRECT = 5;
    private static final int QUALITY_INCORRECT = 1;
    private static final int QUALITY_CORRECT_THRESHOLD = 3;

    /**
     * Applies one review outcome to a card, returning the same instance
     * with updated scheduling state. Pure with respect to "now" being
     * passed in, so it's fully deterministic and unit-testable against
     * known SM-2 reference sequences without any wall-clock dependency.
     */
    public ReviewCard review(ReviewCard card, boolean answeredCorrectly, Instant now) {
        int quality = answeredCorrectly ? QUALITY_CORRECT : QUALITY_INCORRECT;

        double updatedEasiness =
                card.getEasinessFactor()
                        + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
        if (updatedEasiness < MIN_EASINESS_FACTOR) {
            updatedEasiness = MIN_EASINESS_FACTOR;
        }
        card.setEasinessFactor(updatedEasiness);

        if (quality < QUALITY_CORRECT_THRESHOLD) {
            card.setRepetitions(0);
            card.setIntervalDays(1);
        } else {
            int repetitions = card.getRepetitions() + 1;
            int interval;
            if (repetitions == 1) {
                interval = 1;
            } else if (repetitions == 2) {
                interval = 6;
            } else {
                interval = (int) Math.round(card.getIntervalDays() * updatedEasiness);
            }
            card.setRepetitions(repetitions);
            card.setIntervalDays(interval);
        }

        card.setLastReviewedAt(now);
        card.setDueDate(LocalDate.ofInstant(now, java.time.ZoneOffset.UTC).plusDays(card.getIntervalDays()));
        return card;
    }
}
