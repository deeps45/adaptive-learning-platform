package com.learning.platform.controller;

import com.learning.platform.dto.QuizDtos.CreateQuizRequest;
import com.learning.platform.dto.QuizDtos.QuizView;
import com.learning.platform.entity.Quiz;
import com.learning.platform.security.UserPrincipal;
import com.learning.platform.service.QuizService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/api/courses/{courseId}/quizzes")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<QuizView> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateQuizRequest request) {
        Quiz quiz = quizService.createQuiz(principal.id(), courseId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(quizService.toStudentView(quiz));
    }

    @GetMapping("/api/courses/{courseId}/quizzes")
    public List<QuizView> listForCourse(@PathVariable UUID courseId) {
        return quizService.listQuizzesForCourse(courseId).stream().map(quizService::toStudentView).toList();
    }

    @GetMapping("/api/quizzes/{id}")
    public QuizView get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        Quiz quiz = quizService.getQuizEntity(id);
        quizService.requireCanAccessQuiz(principal.id(), principal.role(), quiz);
        return quizService.toStudentView(quiz);
    }
}
