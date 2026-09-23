package com.learning.platform.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "choices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Choice {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;
}
