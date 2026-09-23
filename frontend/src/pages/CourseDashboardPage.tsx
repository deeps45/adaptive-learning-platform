import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getDashboard } from '../api/courses'
import type { CourseDashboard } from '../types'

export function CourseDashboardPage() {
  const { courseId } = useParams<{ courseId: string }>()
  const [dashboard, setDashboard] = useState<CourseDashboard | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!courseId) return
    getDashboard(courseId)
      .then(setDashboard)
      .catch(() => setError('You do not have access to this dashboard.'))
  }, [courseId])

  if (error) return <p className="p-8 text-red-600">{error}</p>
  if (!dashboard) return <p className="p-8 text-slate-500">Loading…</p>

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6">
      <h1 className="text-2xl font-semibold text-slate-900">{dashboard.courseTitle} — Analytics</h1>

      <div className="grid grid-cols-3 gap-4">
        <div className="rounded-lg border border-slate-200 p-4">
          <p className="text-2xl font-semibold text-slate-900">{dashboard.enrolledStudents}</p>
          <p className="text-sm text-slate-500">Enrolled students</p>
        </div>
        <div className="rounded-lg border border-slate-200 p-4">
          <p className="text-2xl font-semibold text-slate-900">{dashboard.totalQuizzes}</p>
          <p className="text-sm text-slate-500">Quizzes</p>
        </div>
        <div className="rounded-lg border border-slate-200 p-4">
          <p className="text-2xl font-semibold text-slate-900">{dashboard.classAverageScorePercent.toFixed(0)}%</p>
          <p className="text-sm text-slate-500">Class average</p>
        </div>
      </div>

      <section>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Students</h2>
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-slate-500">
              <th className="py-2">Name</th>
              <th className="py-2">Attempts</th>
              <th className="py-2">Average score</th>
            </tr>
          </thead>
          <tbody>
            {dashboard.students.map((s) => (
              <tr key={s.studentId} className="border-b border-slate-100">
                <td className="py-2">{s.studentName}</td>
                <td className="py-2">{s.attemptsSubmitted}</td>
                <td className="py-2">{s.attemptsSubmitted > 0 ? `${s.averageScorePercent.toFixed(0)}%` : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {dashboard.students.length === 0 && <p className="text-sm text-slate-500">No students enrolled yet.</p>}
      </section>
    </div>
  )
}
