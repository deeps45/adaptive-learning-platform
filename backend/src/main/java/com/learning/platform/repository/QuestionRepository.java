package com.learning.platform.repository;

import com.learning.platform.entity.Question;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, UUID> {
    List<Question> findByQuizId(UUID quizId);
}
