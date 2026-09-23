package com.learning.platform.service;

import com.learning.platform.dto.CourseDtos.CourseDashboardResponse;
import com.learning.platform.dto.CourseDtos.CreateCourseRequest;
import com.learning.platform.dto.CourseDtos.StudentSummary;
import com.learning.platform.entity.Course;
import com.learning.platform.entity.Enrollment;
import com.learning.platform.entity.QuizAttempt;
import com.learning.platform.entity.User;
import com.learning.platform.entity.AttemptStatus;
import com.learning.platform.exception.ConflictException;
import com.learning.platform.exception.ForbiddenException;
import com.learning.platform.exception.NotFoundException;
import com.learning.platform.repository.CourseRepository;
import com.learning.platform.repository.EnrollmentRepository;
import com.learning.platform.repository.QuizAttemptRepository;
import com.learning.platform.repository.QuizRepository;
import com.learning.platform.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    public CourseService(
            CourseRepository courseRepository,
            UserRepository userRepository,
            EnrollmentRepository enrollmentRepository,
            QuizRepository quizRepository,
            QuizAttemptRepository quizAttemptRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.quizRepository = quizRepository;
        this.quizAttemptRepository = quizAttemptRepository;
    }

    public Course createCourse(UUID instructorId, CreateCourseRequest request) {
        User instructor = requireUser(instructorId);
        Course course =
                Course.builder()
                        .title(request.title())
                        .description(request.description())
                        .instructor(instructor)
                        .build();
        return courseRepository.save(course);
    }

    public List<Course> listCourses() {
        return courseRepository.findAllWithInstructor();
    }

    public Course getCourse(UUID courseId) {
        return courseRepository
                .findByIdWithInstructor(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found"));
    }

    @Transactional
    public void enroll(UUID studentId, UUID courseId) {
        User student = requireUser(studentId);
        Course course = getCourse(courseId);
        if (enrollmentRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            throw new ConflictException("Already enrolled in this course");
        }
        enrollmentRepository.save(Enrollment.builder().student(student).course(course).build());
    }

    /**
     * Ownership check that no {@code @PreAuthorize} role expression alone can express: STUDENT
     * vs INSTRUCTOR is a role, but "is this instructor's own course" is data-dependent, so it's
     * verified here against the actual row, not assumed from the JWT's role claim. Called by
     * every instructor-only, course-scoped operation - see QuizConcurrencyIntegrationTest's RBAC
     * cases for why this specifically (not just role) is what's tested.
     */
    public Course requireOwnedCourse(UUID instructorId, UUID courseId) {
        Course course = getCourse(courseId);
        if (!course.getInstructor().getId().equals(instructorId)) {
            throw new ForbiddenException("You do not own this course");
        }
        return course;
    }

    @Transactional(readOnly = true)
    public CourseDashboardResponse getDashboard(UUID instructorId, UUID courseId) {
        Course course = requireOwnedCourse(instructorId, courseId);
        List<Enrollment> enrollments = enrollmentRepository.findByCourseId(courseId);
        int totalQuizzes = (int) quizRepository.countByCourseId(courseId);

        List<StudentSummary> summaries =
                enrollments.stream()
                        .map(
                                enrollment -> {
                                    User student = enrollment.getStudent();
                                    List<QuizAttempt> attempts =
                                            quizAttemptRepository.findByStudentId(student.getId()).stream()
                                                    .filter(a -> a.getStatus() == AttemptStatus.SUBMITTED)
                                                    .filter(a -> a.getQuiz().getCourse().getId().equals(courseId))
                                                    .toList();
                                    double avg =
                                            attempts.isEmpty()
                                                    ? 0.0
                                                    : attempts.stream()
                                                            .mapToDouble(
                                                                    a -> 100.0 * a.getScore() / a.getTotalQuestions())
                                                            .average()
                                                            .orElse(0.0);
                                    return new StudentSummary(student.getId(), student.getFullName(), attempts.size(), avg);
                                })
                        .toList();

        double classAverage =
                summaries.stream()
                        .filter(s -> s.attemptsSubmitted() > 0)
                        .mapToDouble(StudentSummary::averageScorePercent)
                        .average()
                        .orElse(0.0);

        return new CourseDashboardResponse(
                course.getId(), course.getTitle(), enrollments.size(), totalQuizzes, classAverage, summaries);
    }

    public boolean isEnrolled(UUID studentId, UUID courseId) {
        return enrollmentRepository.existsByStudentIdAndCourseId(studentId, courseId);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
