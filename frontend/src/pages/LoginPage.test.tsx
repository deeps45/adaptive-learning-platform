import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'

const loginMock = vi.fn()

vi.mock('../hooks/AuthContext', () => ({
  useAuth: () => ({ login: loginMock, register: vi.fn(), logout: vi.fn(), user: null }),
}))

describe('LoginPage', () => {
  beforeEach(() => {
    loginMock.mockReset()
  })

  it('submits the entered email and password', async () => {
    loginMock.mockResolvedValue(undefined)
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    )

    await userEvent.type(screen.getByLabelText(/email/i), 'student@example.com')
    await userEvent.type(screen.getByLabelText(/password/i), 'password123')
    await userEvent.click(screen.getByRole('button', { name: /log in/i }))

    await waitFor(() => {
      expect(loginMock).toHaveBeenCalledWith('student@example.com', 'password123')
    })
  })

  it('shows an error message when login fails', async () => {
    loginMock.mockRejectedValue(new Error('bad credentials'))
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    )

    await userEvent.type(screen.getByLabelText(/email/i), 'student@example.com')
    await userEvent.type(screen.getByLabelText(/password/i), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: /log in/i }))

    expect(await screen.findByText(/invalid email or password/i)).toBeInTheDocument()
  })
})
