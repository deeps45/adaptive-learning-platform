package com.learning.platform.repository;

import com.learning.platform.entity.Course;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    List<Course> findByInstructorId(UUID instructorId);

    // instructor is a lazy @ManyToOne; CourseController.toResponse() reads
    // course.getInstructor().getFullName() after the repository call returns, outside any open
    // Hibernate session - plain findAll()/findById() would throw LazyInitializationException the
    // first time that's touched (a real bug this project's own manual browser testing caught,
    // not a hypothetical - same root cause as QuizRepository's equivalent fetch-join fix).
    @Query("select c from Course c join fetch c.instructor")
    List<Course> findAllWithInstructor();

    @Query("select c from Course c join fetch c.instructor where c.id = :id")
    Optional<Course> findByIdWithInstructor(UUID id);
}
