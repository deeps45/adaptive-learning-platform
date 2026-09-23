import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getQuiz, startAttempt, submitAttempt } from '../api/quizzes'
import type { AttemptResult, QuizView } from '../types'

export function QuizTakingPage() {
  const { quizId } = useParams<{ quizId: string }>()
  const [quiz, setQuiz] = useState<QuizView | null>(null)
  const [attemptId, setAttemptId] = useState<string | null>(null)
  const [answers, setAnswers] = useState<Record<string, string>>({})
  const [result, setResult] = useState<AttemptResult | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!quizId) return
    getQuiz(quizId)
      .then(setQuiz)
      .catch(() => setError('You do not have access to this quiz.'))
  }, [quizId])

  async function handleStart() {
    if (!quizId) return
    const id = await startAttempt(quizId)
    setAttemptId(id)
  }

  async function handleSubmit() {
    if (!attemptId || !quiz) return
    setSubmitting(true)
    setError(null)
    try {
      const res = await submitAttempt(
        attemptId,
        quiz.questions.map((q) => ({ questionId: q.id, selectedChoiceId: answers[q.id] ?? null })),
      )
      setResult(res)
    } catch {
      setError('Could not submit quiz')
    } finally {
      setSubmitting(false)
    }
  }

  if (error && !quiz) return <p className="p-8 text-red-600">{error}</p>
  if (!quiz) return <p className="p-8 text-slate-500">Loading…</p>

  if (result) {
    return (
      <div className="mx-auto max-w-2xl space-y-6 p-6">
        <h1 className="text-2xl font-semibold text-slate-900">Results</h1>
        <p className="text-lg text-slate-700">
          {result.score} / {result.totalQuestions} ({result.percent.toFixed(0)}%)
        </p>
        <p className="text-sm text-slate-500">
          Each question you just answered has been scheduled for spaced-repetition review based on
          whether you got it right - check your dashboard's "due for review" list later.
        </p>
        <ul className="space-y-2">
          {quiz.questions.map((q) => {
            const answerResult = result.answers.find((a) => a.questionId === q.id)
            return (
              <li
                key={q.id}
                className={`rounded-md border p-3 text-sm ${
                  answerResult?.correct ? 'border-green-200 bg-green-50' : 'border-red-200 bg-red-50'
                }`}
              >
                {q.text} — {answerResult?.correct ? 'Correct' : 'Incorrect'}
              </li>
            )
          })}
        </ul>
      </div>
    )
  }

  if (!attemptId) {
    return (
      <div className="mx-auto max-w-2xl space-y-4 p-6">
        <h1 className="text-2xl font-semibold text-slate-900">{quiz.title}</h1>
        <p className="text-slate-600">{quiz.description}</p>
        <p className="text-sm text-slate-500">{quiz.questions.length} questions</p>
        <button
          onClick={handleStart}
          className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
        >
          Start quiz
        </button>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6 p-6">
      <h1 className="text-2xl font-semibold text-slate-900">{quiz.title}</h1>
      {quiz.questions.map((q, i) => (
        <div key={q.id} className="space-y-2 rounded-lg border border-slate-200 p-4">
          <p className="font-medium text-slate-800">
            {i + 1}. {q.text}
          </p>
          {q.choices.map((c) => (
            <label key={c.id} className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="radio"
                name={q.id}
                checked={answers[q.id] === c.id}
                onChange={() => setAnswers((prev) => ({ ...prev, [q.id]: c.id }))}
              />
              {c.text}
            </label>
          ))}
        </div>
      ))}
      {error && <p className="text-sm text-red-600">{error}</p>}
      <button
        onClick={handleSubmit}
        disabled={submitting}
        className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
      >
        {submitting ? 'Submitting…' : 'Submit'}
      </button>
    </div>
  )
}
