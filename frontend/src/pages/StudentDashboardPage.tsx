import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listCourses } from '../api/courses'
import { getMyProgress } from '../api/progress'
import type { Course, StudentProgress } from '../types'

export function StudentDashboardPage() {
  const [courses, setCourses] = useState<Course[]>([])
  const [progress, setProgress] = useState<StudentProgress | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([listCourses(), getMyProgress()]).then(([c, p]) => {
      setCourses(c)
      setProgress(p)
      setLoading(false)
    })
  }, [])

  if (loading) return <p className="p-8 text-slate-500">Loading…</p>

  return (
    <div className="mx-auto max-w-5xl space-y-8 p-6">
      <section>
        <h1 className="mb-4 text-2xl font-semibold text-slate-900">Your progress</h1>
        {progress && (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <StatCard label="Quizzes attempted" value={progress.quizzesAttempted} />
            <StatCard label="Average score" value={`${progress.averageScorePercent.toFixed(0)}%`} />
            <StatCard label="Due for review" value={progress.cardsDueForReview} highlight={progress.cardsDueForReview > 0} />
            <StatCard label="Mastered" value={progress.cardsMastered} />
          </div>
        )}
      </section>

      {progress && progress.dueReviews.length > 0 && (
        <section>
          <h2 className="mb-3 text-lg font-semibold text-slate-900">
            Due for review ({progress.dueReviews.length})
          </h2>
          <p className="mb-3 text-sm text-slate-500">
            These are questions you've answered before, scheduled for review by the spaced-repetition
            algorithm - retake the quiz to refresh them.
          </p>
          <ul className="space-y-2">
            {progress.dueReviews.map((r) => (
              <li key={r.questionId} className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm">
                <Link to={`/quizzes/${r.quizId}`} className="font-medium text-slate-900 hover:underline">
                  {r.quizTitle}
                </Link>
                <p className="text-slate-600">{r.questionText}</p>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">All courses</h2>
        <ul className="grid gap-3 sm:grid-cols-2">
          {courses.map((c) => (
            <li key={c.id} className="rounded-lg border border-slate-200 p-4">
              <Link to={`/courses/${c.id}`} className="font-medium text-slate-900 hover:underline">
                {c.title}
              </Link>
              <p className="mt-1 text-sm text-slate-500">{c.description}</p>
              <p className="mt-2 text-xs text-slate-400">by {c.instructorName}</p>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}

function StatCard({ label, value, highlight }: { label: string; value: string | number; highlight?: boolean }) {
  return (
    <div className={`rounded-lg border p-4 ${highlight ? 'border-amber-300 bg-amber-50' : 'border-slate-200'}`}>
      <p className="text-2xl font-semibold text-slate-900">{value}</p>
      <p className="text-sm text-slate-500">{label}</p>
    </div>
  )
}
