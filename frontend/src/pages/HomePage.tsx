import { Navigate } from 'react-router-dom'
import { useAuth } from '../hooks/AuthContext'

export function HomePage() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  return <Navigate to={user.role === 'INSTRUCTOR' ? '/instructor' : '/student'} replace />
}
