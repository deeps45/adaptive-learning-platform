import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

const ACCESS_TOKEN_KEY = 'learning_platform_access_token'
const REFRESH_TOKEN_KEY = 'learning_platform_refresh_token'

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Refreshes the access token exactly once per failing request on a 401, then retries it - a
// second 401 after that (refresh token itself expired/invalid) is treated as "really logged
// out" rather than retried again, which is what the `_retry` flag below prevents: without it, a
// permanently-invalid refresh token would otherwise loop the app into repeated refresh attempts.
let refreshInFlight: Promise<string> | null = null

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
    if (error.response?.status !== 401 || !original || original._retry) {
      return Promise.reject(error)
    }
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
      clearTokens()
      return Promise.reject(error)
    }

    original._retry = true
    try {
      if (!refreshInFlight) {
        refreshInFlight = axios
          .post('/api/auth/refresh', { refreshToken })
          .then((res) => {
            setTokens(res.data.accessToken, res.data.refreshToken)
            return res.data.accessToken as string
          })
          .finally(() => {
            refreshInFlight = null
          })
      }
      const newAccessToken = await refreshInFlight
      original.headers = original.headers ?? {}
      original.headers.Authorization = `Bearer ${newAccessToken}`
      return api(original)
    } catch (refreshError) {
      clearTokens()
      return Promise.reject(refreshError)
    }
  },
)
