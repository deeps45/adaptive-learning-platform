package com.learning.platform.repository;

import com.learning.platform.entity.Enrollment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    boolean existsByStudentIdAndCourseId(UUID studentId, UUID courseId);

    Optional<Enrollment> findByStudentIdAndCourseId(UUID studentId, UUID courseId);

    List<Enrollment> findByCourseId(UUID courseId);

    List<Enrollment> findByStudentId(UUID studentId);
}
