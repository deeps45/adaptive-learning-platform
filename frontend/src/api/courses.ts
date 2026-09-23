import { api } from './client'
import type { Course, CourseDashboard } from '../types'

export async function listCourses(): Promise<Course[]> {
  const res = await api.get<Course[]>('/courses')
  return res.data
}

export async function getCourse(id: string): Promise<Course> {
  const res = await api.get<Course>(`/courses/${id}`)
  return res.data
}

export async function createCourse(title: string, description: string): Promise<Course> {
  const res = await api.post<Course>('/courses', { title, description })
  return res.data
}

export async function enroll(courseId: string): Promise<void> {
  await api.post(`/courses/${courseId}/enroll`)
}

export async function getDashboard(courseId: string): Promise<CourseDashboard> {
  const res = await api.get<CourseDashboard>(`/courses/${courseId}/dashboard`)
  return res.data
}
