package com.learning.platform.repository;

import com.learning.platform.entity.AttemptAnswer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, UUID> {
    long countByAttemptId(UUID attemptId);
}
