package com.learning.platform.controller;

import com.learning.platform.dto.QuizDtos.AttemptResult;
import com.learning.platform.dto.QuizDtos.SubmitAttemptRequest;
import com.learning.platform.entity.QuizAttempt;
import com.learning.platform.security.UserPrincipal;
import com.learning.platform.service.QuizAttemptService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class AttemptController {

    private final QuizAttemptService attemptService;

    public AttemptController(QuizAttemptService attemptService) {
        this.attemptService = attemptService;
    }

    @PostMapping("/api/quizzes/{quizId}/attempts")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<java.util.Map<String, UUID>> start(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID quizId) {
        QuizAttempt attempt = attemptService.startAttempt(principal.id(), quizId);
        return ResponseEntity.status(HttpStatus.CREATED).body(java.util.Map.of("attemptId", attempt.getId()));
    }

    @PostMapping("/api/attempts/{attemptId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public AttemptResult submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID attemptId,
            @Valid @RequestBody SubmitAttemptRequest request) {
        return attemptService.submit(principal.id(), attemptId, request);
    }
}
