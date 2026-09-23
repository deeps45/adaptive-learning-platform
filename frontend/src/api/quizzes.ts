import { api } from './client'
import type { AttemptResult, QuizView } from '../types'

export interface CreateChoiceInput {
  text: string
  correct: boolean
}

export interface CreateQuestionInput {
  text: string
  choices: CreateChoiceInput[]
}

export async function listQuizzesForCourse(courseId: string): Promise<QuizView[]> {
  const res = await api.get<QuizView[]>(`/courses/${courseId}/quizzes`)
  return res.data
}

export async function getQuiz(quizId: string): Promise<QuizView> {
  const res = await api.get<QuizView>(`/quizzes/${quizId}`)
  return res.data
}

export async function createQuiz(
  courseId: string,
  title: string,
  description: string,
  questions: CreateQuestionInput[],
): Promise<QuizView> {
  const res = await api.post<QuizView>(`/courses/${courseId}/quizzes`, { title, description, questions })
  return res.data
}

export async function startAttempt(quizId: string): Promise<string> {
  const res = await api.post<{ attemptId: string }>(`/quizzes/${quizId}/attempts`)
  return res.data.attemptId
}

export async function submitAttempt(
  attemptId: string,
  answers: { questionId: string; selectedChoiceId: string | null }[],
): Promise<AttemptResult> {
  const res = await api.post<AttemptResult>(`/attempts/${attemptId}/submit`, { answers })
  return res.data
}
