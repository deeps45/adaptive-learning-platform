import { api } from './client'
import type { StudentProgress } from '../types'

export async function getMyProgress(): Promise<StudentProgress> {
  const res = await api.get<StudentProgress>('/students/me/progress')
  return res.data
}
