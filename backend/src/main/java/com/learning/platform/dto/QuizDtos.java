package com.learning.platform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public final class QuizDtos {
    private QuizDtos() {}

    public record CreateChoiceRequest(@NotBlank String text, boolean correct) {}

    public record CreateQuestionRequest(
            @NotBlank String text, @NotEmpty @Valid List<CreateChoiceRequest> choices) {}

    public record CreateQuizRequest(
            @NotBlank String title,
            String description,
            @NotEmpty @Valid List<CreateQuestionRequest> questions) {}

    // Student-facing view: choices are shown, but which one is correct is never sent to the
    // client before grading - see QuizService.toStudentView(). Leaking that in the JSON payload
    // would let anyone answer every question correctly by reading the network tab.
    public record ChoiceView(UUID id, String text) {}

    public record QuestionView(UUID id, String text, List<ChoiceView> choices) {}

    public record QuizView(UUID id, UUID courseId, String title, String description, List<QuestionView> questions) {}

    public record SubmitAnswerRequest(@NotNull UUID questionId, UUID selectedChoiceId) {}

    public record SubmitAttemptRequest(@NotEmpty @Valid List<SubmitAnswerRequest> answers) {}

    public record AnswerResult(UUID questionId, UUID selectedChoiceId, UUID correctChoiceId, boolean correct) {}

    public record AttemptResult(
            UUID attemptId, int score, int totalQuestions, double percent, List<AnswerResult> answers) {}
}
