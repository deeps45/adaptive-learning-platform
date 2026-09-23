import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { createCourse, listCourses } from '../api/courses'
import { useAuth } from '../hooks/AuthContext'
import type { Course } from '../types'

export function InstructorDashboardPage() {
  const { user } = useAuth()
  const [courses, setCourses] = useState<Course[]>([])
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    listCourses().then(setCourses)
  }, [])

  const myCourses = courses.filter((c) => c.instructorId === user?.id)

  async function handleCreate(e: FormEvent) {
    e.preventDefault()
    setCreating(true)
    try {
      const course = await createCourse(title, description)
      setCourses((prev) => [...prev, course])
      setTitle('')
      setDescription('')
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-8 p-6">
      <section>
        <h1 className="mb-4 text-2xl font-semibold text-slate-900">Your courses</h1>
        <ul className="grid gap-3 sm:grid-cols-2">
          {myCourses.map((c) => (
            <li key={c.id} className="rounded-lg border border-slate-200 p-4">
              <Link to={`/courses/${c.id}`} className="font-medium text-slate-900 hover:underline">
                {c.title}
              </Link>
              <p className="mt-1 text-sm text-slate-500">{c.description}</p>
              <Link to={`/courses/${c.id}/dashboard`} className="mt-2 inline-block text-sm text-blue-600 hover:underline">
                View analytics →
              </Link>
            </li>
          ))}
          {myCourses.length === 0 && <p className="text-sm text-slate-500">No courses yet - create one below.</p>}
        </ul>
      </section>

      <section>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Create a course</h2>
        <form onSubmit={handleCreate} className="max-w-md space-y-3">
          <input
            required
            placeholder="Title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
          />
          <textarea
            placeholder="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
          />
          <button
            type="submit"
            disabled={creating}
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
          >
            {creating ? 'Creating…' : 'Create course'}
          </button>
        </form>
      </section>
    </div>
  )
}
