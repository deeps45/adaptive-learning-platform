CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE courses (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    instructor_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_courses_instructor ON courses(instructor_id);

CREATE TABLE enrollments (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES users(id),
    course_id UUID NOT NULL REFERENCES courses(id),
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (student_id, course_id)
);
CREATE INDEX idx_enrollments_course ON enrollments(course_id);
CREATE INDEX idx_enrollments_student ON enrollments(student_id);

CREATE TABLE quizzes (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_quizzes_course ON quizzes(course_id);

CREATE TABLE questions (
    id UUID PRIMARY KEY,
    quiz_id UUID NOT NULL REFERENCES quizzes(id),
    text TEXT NOT NULL,
    order_index INT NOT NULL
);
CREATE INDEX idx_questions_quiz ON questions(quiz_id);

CREATE TABLE choices (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES questions(id),
    text TEXT NOT NULL,
    order_index INT NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX idx_choices_question ON choices(question_id);

CREATE TABLE quiz_attempts (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES users(id),
    quiz_id UUID NOT NULL REFERENCES quizzes(id),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at TIMESTAMPTZ,
    score INT,
    total_questions INT,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_attempts_student_quiz ON quiz_attempts(student_id, quiz_id);

CREATE TABLE attempt_answers (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES quiz_attempts(id),
    question_id UUID NOT NULL REFERENCES questions(id),
    selected_choice_id UUID REFERENCES choices(id),
    correct BOOLEAN NOT NULL
);
CREATE INDEX idx_attempt_answers_attempt ON attempt_answers(attempt_id);

CREATE TABLE review_cards (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES users(id),
    question_id UUID NOT NULL REFERENCES questions(id),
    easiness_factor DOUBLE PRECISION NOT NULL DEFAULT 2.5,
    interval_days INT NOT NULL DEFAULT 0,
    repetitions INT NOT NULL DEFAULT 0,
    due_date DATE NOT NULL,
    last_reviewed_at TIMESTAMPTZ,
    UNIQUE (student_id, question_id)
);
CREATE INDEX idx_review_cards_student_due ON review_cards(student_id, due_date);
