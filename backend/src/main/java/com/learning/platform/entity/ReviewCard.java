package com.learning.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

// Per-(student, question) spaced-repetition state, evolved by the SM-2
// algorithm (see SpacedRepetitionService) every time the student answers
// this question in a submitted quiz attempt. This is the actual
// differentiator of this project over a typical quiz-and-score app: a
// question answered correctly gets pushed further into the future
// (dueDate), one answered incorrectly comes back tomorrow, and
// easinessFactor adapts per-question, per-student over time.
@Entity
@Table(
        name = "review_cards",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "question_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewCard {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    // SuperMemo SM-2's "E-Factor" - how easy this card is for this student.
    // Starts at 2.5 (SM-2's canonical default) and never drops below 1.3
    // (SM-2's floor - below that the algorithm degenerates).
    @Column(name = "easiness_factor", nullable = false)
    @Builder.Default
    private double easinessFactor = 2.5;

    @Column(name = "interval_days", nullable = false)
    @Builder.Default
    private int intervalDays = 0;

    @Column(nullable = false)
    @Builder.Default
    private int repetitions = 0;

    @Column(name = "due_date", nullable = false)
    @Builder.Default
    private LocalDate dueDate = LocalDate.now();

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;
}
