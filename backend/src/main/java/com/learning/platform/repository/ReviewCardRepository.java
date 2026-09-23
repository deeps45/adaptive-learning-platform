package com.learning.platform.repository;

import com.learning.platform.entity.ReviewCard;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewCardRepository extends JpaRepository<ReviewCard, UUID> {
    Optional<ReviewCard> findByStudentIdAndQuestionId(UUID studentId, UUID questionId);

    List<ReviewCard> findByStudentIdAndDueDateLessThanEqualOrderByDueDateAsc(
            UUID studentId, LocalDate date);

    List<ReviewCard> findByStudentId(UUID studentId);
}
