package com.learning.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "quizzes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Set, not List: Hibernate can't JOIN FETCH two List ("bag") collections in the same query
    // (MultipleBagFetchException) - see QuizRepository.findByIdWithQuestionsAndChoices(), which
    // needs to fetch both this and Question.choices together. @OrderBy still produces a
    // consistently ordered (LinkedHashSet-backed) result despite the field type change.
    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private Set<Question> questions = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
