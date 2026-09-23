package com.learning.platform.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class CourseDtos {
    private CourseDtos() {}

    public record CreateCourseRequest(@NotBlank String title, String description) {}

    public record CourseResponse(
            UUID id, String title, String description, UUID instructorId, String instructorName, Instant createdAt) {}

    public record StudentSummary(UUID studentId, String studentName, int attemptsSubmitted, double averageScorePercent) {}

    public record CourseDashboardResponse(
            UUID courseId,
            String courseTitle,
            int enrolledStudents,
            int totalQuizzes,
            double classAverageScorePercent,
            java.util.List<StudentSummary> students) {}
}
