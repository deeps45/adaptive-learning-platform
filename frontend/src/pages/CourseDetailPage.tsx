import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { enroll, getCourse } from '../api/courses'
import { listQuizzesForCourse } from '../api/quizzes'
import { useAuth } from '../hooks/AuthContext'
import type { Course, QuizView } from '../types'

export function CourseDetailPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const { user } = useAuth()
  const [course, setCourse] = useState<Course | null>(null)
  const [quizzes, setQuizzes] = useState<QuizView[]>([])
  const [enrolling, setEnrolling] = useState(false)
  const [enrolled, setEnrolled] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!courseId) return
    getCourse(courseId).then(setCourse)
    listQuizzesForCourse(courseId)
      .then(setQuizzes)
      .catch(() => setQuizzes([])) // 403 if a student isn't enrolled yet - fine, just show none
  }, [courseId])

  const isOwner = user?.role === 'INSTRUCTOR' && course?.instructorId === user.id
  const isStudent = user?.role === 'STUDENT'

  async function handleEnroll() {
    if (!courseId) return
    setEnrolling(true)
    setError(null)
    try {
      await enroll(courseId)
      setEnrolled(true)
      const q = await listQuizzesForCourse(courseId)
      setQuizzes(q)
    } catch {
      setError('Could not enroll')
    } finally {
      setEnrolling(false)
    }
  }

  if (!course) return <p className="p-8 text-slate-500">Loading…</p>

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">{course.title}</h1>
        <p className="mt-1 text-slate-600">{course.description}</p>
        <p className="mt-1 text-sm text-slate-400">by {course.instructorName}</p>
      </div>

      {isStudent && !enrolled && (
        <button
          onClick={handleEnroll}
          disabled={enrolling}
          className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
        >
          {enrolling ? 'Enrolling…' : 'Enroll in this course'}
        </button>
      )}
      {error && <p className="text-sm text-red-600">{error}</p>}

      {isOwner && (
        <Link
          to={`/courses/${course.id}/quizzes/new`}
          className="inline-block rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
        >
          + New quiz
        </Link>
      )}

      <section>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Quizzes</h2>
        {quizzes.length === 0 && (
          <p className="text-sm text-slate-500">
            {isStudent && !enrolled ? 'Enroll to see this course’s quizzes.' : 'No quizzes yet.'}
          </p>
        )}
        <ul className="space-y-2">
          {quizzes.map((q) => (
            <li key={q.id} className="rounded-md border border-slate-200 p-3">
              <Link to={`/quizzes/${q.id}`} className="font-medium text-slate-900 hover:underline">
                {q.title}
              </Link>
              <p className="text-sm text-slate-500">{q.questions.length} questions</p>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
