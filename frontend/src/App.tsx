import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { NavBar } from './components/NavBar'
import { ProtectedRoute } from './components/ProtectedRoute'
import { AuthProvider } from './hooks/AuthContext'
import { CourseDashboardPage } from './pages/CourseDashboardPage'
import { CourseDetailPage } from './pages/CourseDetailPage'
import { HomePage } from './pages/HomePage'
import { InstructorDashboardPage } from './pages/InstructorDashboardPage'
import { LoginPage } from './pages/LoginPage'
import { QuizBuilderPage } from './pages/QuizBuilderPage'
import { QuizTakingPage } from './pages/QuizTakingPage'
import { RegisterPage } from './pages/RegisterPage'
import { StudentDashboardPage } from './pages/StudentDashboardPage'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <div className="min-h-screen bg-slate-50">
          <NavBar />
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route
              path="/student"
              element={
                <ProtectedRoute role="STUDENT">
                  <StudentDashboardPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/instructor"
              element={
                <ProtectedRoute role="INSTRUCTOR">
                  <InstructorDashboardPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/courses/:courseId"
              element={
                <ProtectedRoute>
                  <CourseDetailPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/courses/:courseId/quizzes/new"
              element={
                <ProtectedRoute role="INSTRUCTOR">
                  <QuizBuilderPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/courses/:courseId/dashboard"
              element={
                <ProtectedRoute role="INSTRUCTOR">
                  <CourseDashboardPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/quizzes/:quizId"
              element={
                <ProtectedRoute role="STUDENT">
                  <QuizTakingPage />
                </ProtectedRoute>
              }
            />
          </Routes>
        </div>
      </BrowserRouter>
    </AuthProvider>
  )
}
