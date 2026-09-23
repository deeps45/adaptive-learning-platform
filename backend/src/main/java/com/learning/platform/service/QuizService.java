package com.learning.platform.service;

import com.learning.platform.dto.QuizDtos.ChoiceView;
import com.learning.platform.dto.QuizDtos.CreateQuestionRequest;
import com.learning.platform.dto.QuizDtos.CreateQuizRequest;
import com.learning.platform.dto.QuizDtos.QuestionView;
import com.learning.platform.dto.QuizDtos.QuizView;
import com.learning.platform.entity.Choice;
import com.learning.platform.entity.Course;
import com.learning.platform.entity.Question;
import com.learning.platform.entity.Quiz;
import com.learning.platform.exception.ForbiddenException;
import com.learning.platform.exception.NotFoundException;
import com.learning.platform.repository.QuizRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizService {

    private final QuizRepository quizRepository;
    private final CourseService courseService;

    public QuizService(QuizRepository quizRepository, CourseService courseService) {
        this.quizRepository = quizRepository;
        this.courseService = courseService;
    }

    @Transactional
    public Quiz createQuiz(UUID instructorId, UUID courseId, CreateQuizRequest request) {
        Course course = courseService.requireOwnedCourse(instructorId, courseId);

        Quiz quiz = Quiz.builder().course(course).title(request.title()).description(request.description()).build();

        int qIndex = 0;
        for (CreateQuestionRequest qReq : request.questions()) {
            Question question = Question.builder().quiz(quiz).text(qReq.text()).orderIndex(qIndex++).build();
            int cIndex = 0;
            boolean hasCorrect = false;
            for (var cReq : qReq.choices()) {
                Choice choice =
                        Choice.builder()
                                .question(question)
                                .text(cReq.text())
                                .orderIndex(cIndex++)
                                .correct(cReq.correct())
                                .build();
                hasCorrect = hasCorrect || cReq.correct();
                question.getChoices().add(choice);
            }
            if (!hasCorrect) {
                throw new IllegalArgumentException(
                        "Question \"" + qReq.text() + "\" must have at least one correct choice");
            }
            quiz.getQuestions().add(question);
        }

        return quizRepository.save(quiz);
    }

    public List<Quiz> listQuizzesForCourse(UUID courseId) {
        return quizRepository.findByCourseIdWithQuestionsAndChoices(courseId);
    }

    public Quiz getQuizEntity(UUID quizId) {
        return quizRepository
                .findByIdWithQuestionsAndChoices(quizId)
                .orElseThrow(() -> new NotFoundException("Quiz not found"));
    }

    /**
     * Verifies a student may take this quiz (must be enrolled in its course) and, separately,
     * that an instructor viewing it owns the course - both checked against actual enrollment/
     * ownership rows, not inferred from role alone. See CourseService.requireOwnedCourse for why
     * that distinction matters.
     */
    public void requireCanAccessQuiz(UUID userId, String role, Quiz quiz) {
        UUID courseId = quiz.getCourse().getId();
        if ("INSTRUCTOR".equals(role)) {
            courseService.requireOwnedCourse(userId, courseId);
        } else {
            if (!courseService.isEnrolled(userId, courseId)) {
                throw new ForbiddenException("You must be enrolled in this course to view its quizzes");
            }
        }
    }

    /** Student-facing view - never includes which choice is correct (see QuizDtos.ChoiceView). */
    public QuizView toStudentView(Quiz quiz) {
        List<QuestionView> questions =
                quiz.getQuestions().stream()
                        .map(
                                q ->
                                        new QuestionView(
                                                q.getId(),
                                                q.getText(),
                                                q.getChoices().stream()
                                                        .map(c -> new ChoiceView(c.getId(), c.getText()))
                                                        .collect(Collectors.toList())))
                        .collect(Collectors.toList());
        return new QuizView(quiz.getId(), quiz.getCourse().getId(), quiz.getTitle(), quiz.getDescription(), questions);
    }
}
