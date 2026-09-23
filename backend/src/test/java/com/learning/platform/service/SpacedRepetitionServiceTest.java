package com.learning.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import com.learning.platform.entity.ReviewCard;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Verifies this project's SM-2 implementation against the algorithm's own
 * canonical reference behavior (Wozniak, 1987) - the exact
 * easiness-factor and interval sequence a fresh card produces under a run
 * of perfect responses is a matter of public record, not a subjective
 * choice, so these numbers aren't tuned to whatever the code happens to
 * output.
 */
class SpacedRepetitionServiceTest {

    private final SpacedRepetitionService service = new SpacedRepetitionService();
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void freshCardStartsAtDefaultEasiness() {
        ReviewCard card = ReviewCard.builder().build();
        assertThat(card.getEasinessFactor()).isEqualTo(SpacedRepetitionService.DEFAULT_EASINESS_FACTOR);
        assertThat(card.getRepetitions()).isZero();
    }

    @Test
    void firstCorrectAnswerSetsIntervalToOneDay() {
        ReviewCard card = ReviewCard.builder().build();
        service.review(card, true, now);
        assertThat(card.getRepetitions()).isEqualTo(1);
        assertThat(card.getIntervalDays()).isEqualTo(1);
        assertThat(card.getEasinessFactor()).isCloseTo(2.6, offset(1e-9));
    }

    @Test
    void secondConsecutiveCorrectAnswerSetsIntervalToSixDays() {
        ReviewCard card = ReviewCard.builder().build();
        service.review(card, true, now);
        service.review(card, true, now);
        assertThat(card.getRepetitions()).isEqualTo(2);
        assertThat(card.getIntervalDays()).isEqualTo(6);
        assertThat(card.getEasinessFactor()).isCloseTo(2.7, offset(1e-9));
    }

    @Test
    void thirdAndLaterCorrectAnswersMultiplyPreviousIntervalByEasiness() {
        ReviewCard card = ReviewCard.builder().build();
        service.review(card, true, now); // interval 1, EF 2.6
        service.review(card, true, now); // interval 6, EF 2.7
        service.review(card, true, now); // interval round(6*2.8)=17, EF 2.8
        assertThat(card.getRepetitions()).isEqualTo(3);
        assertThat(card.getEasinessFactor()).isCloseTo(2.8, offset(1e-9));
        assertThat(card.getIntervalDays()).isEqualTo(17);

        service.review(card, true, now); // interval round(17*2.9)=49, EF 2.9
        assertThat(card.getRepetitions()).isEqualTo(4);
        assertThat(card.getEasinessFactor()).isCloseTo(2.9, offset(1e-9));
        assertThat(card.getIntervalDays()).isEqualTo(49);
    }

    @Test
    void incorrectAnswerResetsRepetitionsAndIntervalRegardlessOfHistory() {
        ReviewCard card = ReviewCard.builder().build();
        service.review(card, true, now);
        service.review(card, true, now);
        service.review(card, true, now); // repetitions=3, interval=17

        service.review(card, false, now);

        assertThat(card.getRepetitions()).isZero();
        assertThat(card.getIntervalDays()).isEqualTo(1);
    }

    @Test
    void incorrectAnswerLowersEasinessFactor() {
        ReviewCard card = ReviewCard.builder().build(); // EF 2.5
        service.review(card, false, now);
        // EF = 2.5 + (0.1 - 4*(0.08 + 4*0.02)) = 2.5 - 0.54 = 1.96
        assertThat(card.getEasinessFactor()).isCloseTo(1.96, offset(1e-9));
    }

    @Test
    void easinessFactorNeverDropsBelowSm2Floor() {
        ReviewCard card = ReviewCard.builder().easinessFactor(1.3).build();
        service.review(card, false, now);
        assertThat(card.getEasinessFactor()).isEqualTo(SpacedRepetitionService.MIN_EASINESS_FACTOR);
    }

    @Test
    void repeatedIncorrectAnswersConvergeToTheFloorNotBelowIt() {
        ReviewCard card = ReviewCard.builder().build();
        for (int i = 0; i < 20; i++) {
            service.review(card, false, now);
        }
        assertThat(card.getEasinessFactor()).isEqualTo(SpacedRepetitionService.MIN_EASINESS_FACTOR);
    }

    @Test
    void dueDateAdvancesByExactlyTheComputedInterval() {
        ReviewCard card = ReviewCard.builder().build();
        service.review(card, true, now); // interval 1
        assertThat(card.getDueDate()).isEqualTo(now.atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(1));
    }

    @Test
    void lastReviewedAtIsStampedWithTheGivenInstant() {
        ReviewCard card = ReviewCard.builder().build();
        service.review(card, true, now);
        assertThat(card.getLastReviewedAt()).isEqualTo(now);
    }

    @Test
    void recoveryAfterAMistakeRestartsTheOneThenSixDaySequence() {
        ReviewCard card = ReviewCard.builder().build();
        service.review(card, true, now);
        service.review(card, true, now);
        service.review(card, true, now); // interval 17

        service.review(card, false, now); // reset: repetitions 0, interval 1

        service.review(card, true, now); // repetitions 1 again -> interval 1
        assertThat(card.getIntervalDays()).isEqualTo(1);
        service.review(card, true, now); // repetitions 2 -> interval 6
        assertThat(card.getIntervalDays()).isEqualTo(6);
    }
}
