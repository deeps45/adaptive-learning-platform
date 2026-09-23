import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/AuthContext'

export function NavBar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <nav className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
        <Link to="/" className="text-lg font-semibold text-slate-900">
          Adaptive Learning
        </Link>
        <div className="flex items-center gap-4 text-sm">
          {user ? (
            <>
              <span className="text-slate-500">
                {user.email} · <span className="font-medium text-slate-700">{user.role}</span>
              </span>
              <button
                onClick={() => {
                  logout()
                  navigate('/login')
                }}
                className="rounded-md bg-slate-100 px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-200"
              >
                Log out
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="text-slate-600 hover:text-slate-900">
                Log in
              </Link>
              <Link to="/register" className="rounded-md bg-slate-900 px-3 py-1.5 font-medium text-white hover:bg-slate-700">
                Sign up
              </Link>
            </>
          )}
        </div>
      </div>
    </nav>
  )
}
