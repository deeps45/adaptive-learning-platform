import { useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { createQuiz, type CreateQuestionInput } from '../api/quizzes'

function emptyQuestion(): CreateQuestionInput {
  return {
    text: '',
    choices: [
      { text: '', correct: true },
      { text: '', correct: false },
    ],
  }
}

export function QuizBuilderPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [questions, setQuestions] = useState<CreateQuestionInput[]>([emptyQuestion()])
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function updateQuestionText(qi: number, text: string) {
    setQuestions((prev) => prev.map((q, i) => (i === qi ? { ...q, text } : q)))
  }

  function updateChoiceText(qi: number, ci: number, text: string) {
    setQuestions((prev) =>
      prev.map((q, i) =>
        i === qi ? { ...q, choices: q.choices.map((c, j) => (j === ci ? { ...c, text } : c)) } : q,
      ),
    )
  }

  function setCorrectChoice(qi: number, ci: number) {
    setQuestions((prev) =>
      prev.map((q, i) =>
        i === qi ? { ...q, choices: q.choices.map((c, j) => ({ ...c, correct: j === ci })) } : q,
      ),
    )
  }

  function addQuestion() {
    setQuestions((prev) => [...prev, emptyQuestion()])
  }

  function removeQuestion(qi: number) {
    setQuestions((prev) => prev.filter((_, i) => i !== qi))
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!courseId) return
    setError(null)
    setSubmitting(true)
    try {
      // Not /quizzes/${quiz.id} - that route is STUDENT-only (an instructor can design a
      // quiz but not "take" it as a graded attempt, matching the backend's own
      // hasRole('STUDENT') check on POST /quizzes/{id}/attempts), so an instructor landing
      // there would immediately get bounced back out by ProtectedRoute. The course page
      // lists the quiz they just created instead.
      await createQuiz(courseId, title, description, questions)
      navigate(`/courses/${courseId}`)
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { error?: string } } })?.response?.data?.error ?? 'Could not create quiz'
      setError(message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6 p-6">
      <h1 className="text-2xl font-semibold text-slate-900">New quiz</h1>
      <form onSubmit={handleSubmit} className="space-y-6">
        <input
          required
          placeholder="Quiz title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
        <textarea
          placeholder="Description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
        />

        {questions.map((q, qi) => (
          <div key={qi} className="space-y-3 rounded-lg border border-slate-200 p-4">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-slate-700">Question {qi + 1}</span>
              {questions.length > 1 && (
                <button type="button" onClick={() => removeQuestion(qi)} className="text-xs text-red-600">
                  Remove
                </button>
              )}
            </div>
            <input
              required
              placeholder="Question text"
              value={q.text}
              onChange={(e) => updateQuestionText(qi, e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
            {q.choices.map((c, ci) => (
              <div key={ci} className="flex items-center gap-2">
                <input
                  type="radio"
                  checked={c.correct}
                  onChange={() => setCorrectChoice(qi, ci)}
                  title="Mark as correct answer"
                />
                <input
                  required
                  placeholder={`Choice ${ci + 1}`}
                  value={c.text}
                  onChange={(e) => updateChoiceText(qi, ci, e.target.value)}
                  className="flex-1 rounded-md border border-slate-300 px-3 py-1.5 text-sm"
                />
              </div>
            ))}
          </div>
        ))}

        <button type="button" onClick={addQuestion} className="text-sm font-medium text-blue-600 hover:underline">
          + Add question
        </button>

        {error && <p className="text-sm text-red-600">{error}</p>}

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
        >
          {submitting ? 'Creating…' : 'Create quiz'}
        </button>
      </form>
    </div>
  )
}
