package com.learning.platform.controller;

import com.learning.platform.dto.ProgressDtos.StudentProgressResponse;
import com.learning.platform.security.UserPrincipal;
import com.learning.platform.service.ProgressService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students/me")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @GetMapping("/progress")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentProgressResponse progress(@AuthenticationPrincipal UserPrincipal principal) {
        return progressService.getProgress(principal.id());
    }
}
