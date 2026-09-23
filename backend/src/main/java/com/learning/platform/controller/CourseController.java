package com.learning.platform.controller;

import com.learning.platform.dto.CourseDtos.CourseDashboardResponse;
import com.learning.platform.dto.CourseDtos.CourseResponse;
import com.learning.platform.dto.CourseDtos.CreateCourseRequest;
import com.learning.platform.entity.Course;
import com.learning.platform.security.UserPrincipal;
import com.learning.platform.service.CourseService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<CourseResponse> create(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreateCourseRequest request) {
        Course course = courseService.createCourse(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(course));
    }

    @GetMapping
    public List<CourseResponse> list() {
        return courseService.listCourses().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public CourseResponse get(@PathVariable UUID id) {
        return toResponse(courseService.getCourse(id));
    }

    @PostMapping("/{id}/enroll")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> enroll(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        courseService.enroll(principal.id(), id);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{id}/dashboard")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public CourseDashboardResponse dashboard(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return courseService.getDashboard(principal.id(), id);
    }

    private CourseResponse toResponse(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getInstructor().getId(),
                course.getInstructor().getFullName(),
                course.getCreatedAt());
    }
}
