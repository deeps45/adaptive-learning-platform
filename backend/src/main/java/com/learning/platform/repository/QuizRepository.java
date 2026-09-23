package com.learning.platform.repository;

import com.learning.platform.entity.Quiz;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    // A count doesn't need questions/choices eagerly loaded at all - CourseService.getDashboard()
    // only wants "how many", so this is both correct and cheaper than fetching full quiz data.
    long countByCourseId(UUID courseId);

    // Plain findById()/findByCourseId() return Quiz entities whose `questions` (and each
    // question's `choices`) are lazy proxies - fine within an open Hibernate session, but every
    // caller here (QuizService.getQuizEntity(), listQuizzesForCourse()) is invoked from
    // request-handling code that doesn't keep one open past the repository call, and
    // toStudentView() then needs those collections. Without eager-fetch queries like these,
    // that's a LazyInitializationException ("no Session") the first time anything actually reads
    // quiz.getQuestions() - a real bug this project's own integration tests *and* manual browser
    // testing caught in two separate call paths (see QuizFlowIntegrationTest and
    // CourseDetailPage's quiz list, which surfaced the findByCourseId case specifically), not a
    // hypothetical. JOIN FETCH pulls both collections in the same query; DISTINCT is required
    // because fetching two collections in one query produces a row per (question, choice) pair,
    // and without it Hibernate would return duplicate Question objects, one per Choice.
    @Query(
            "select distinct q from Quiz q "
                    + "left join fetch q.questions qu "
                    + "left join fetch qu.choices "
                    + "where q.id = :id")
    Optional<Quiz> findByIdWithQuestionsAndChoices(UUID id);

    @Query(
            "select distinct q from Quiz q "
                    + "left join fetch q.questions qu "
                    + "left join fetch qu.choices "
                    + "where q.course.id = :courseId")
    List<Quiz> findByCourseIdWithQuestionsAndChoices(UUID courseId);
}
