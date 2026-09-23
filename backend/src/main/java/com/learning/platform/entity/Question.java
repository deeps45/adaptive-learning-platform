package com.learning.platform.entity;

import jakarta.persistence.*;
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
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    // Set, not List - see Quiz.questions for why (MultipleBagFetchException).
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private Set<Choice> choices = new LinkedHashSet<>();
}
