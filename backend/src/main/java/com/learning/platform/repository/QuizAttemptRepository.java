package com.learning.platform.repository;

import com.learning.platform.entity.QuizAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {
    List<QuizAttempt> findByStudentIdAndQuizId(UUID studentId, UUID quizId);

    List<QuizAttempt> findByQuizId(UUID quizId);

    List<QuizAttempt> findByStudentId(UUID studentId);

    // PESSIMISTIC_WRITE (SELECT ... FOR UPDATE) so the second of two
    // concurrent submit() calls for the same attempt blocks until the
    // first transaction commits, rather than both reading stale state and
    // racing to write - combined with the entity's @Version, this is
    // belt-and-suspenders idempotency, not just optimistic-locking hope.
    // See QuizAttemptService.submit() and QuizConcurrencyIntegrationTest.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from QuizAttempt a where a.id = :id")
    Optional<QuizAttempt> findByIdForUpdate(UUID id);
}
