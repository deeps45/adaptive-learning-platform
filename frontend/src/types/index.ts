export type Role = 'STUDENT' | 'INSTRUCTOR'

export interface User {
  id: string
  email: string
  fullName: string
  role: Role
}

export interface Course {
  id: string
  title: string
  description: string
  instructorId: string
  instructorName: string
  createdAt: string
}

export interface ChoiceView {
  id: string
  text: string
}

export interface QuestionView {
  id: string
  text: string
  choices: ChoiceView[]
}

export interface QuizView {
  id: string
  courseId: string
  title: string
  description: string
  questions: QuestionView[]
}

export interface AnswerResult {
  questionId: string
  selectedChoiceId: string | null
  correctChoiceId: string
  correct: boolean
}

export interface AttemptResult {
  attemptId: string
  score: number
  totalQuestions: number
  percent: number
  answers: AnswerResult[]
}

export interface DueReviewCard {
  questionId: string
  questionText: string
  quizId: string
  quizTitle: string
  dueDate: string
  repetitions: number
}

export interface StudentProgress {
  quizzesAttempted: number
  averageScorePercent: number
  cardsDueForReview: number
  cardsMastered: number
  dueReviews: DueReviewCard[]
}

export interface StudentSummary {
  studentId: string
  studentName: string
  attemptsSubmitted: number
  averageScorePercent: number
}

export interface CourseDashboard {
  courseId: string
  courseTitle: string
  enrolledStudents: number
  totalQuizzes: number
  classAverageScorePercent: number
  students: StudentSummary[]
}
