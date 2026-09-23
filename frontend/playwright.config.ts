import { defineConfig, devices } from '@playwright/test'

/**
 * Drives the real app in a real browser against a real backend + Postgres - not a mock. This is
 * what actually caught the QuizBuilderPage routing bug (see README "Bugs found by actually
 * running this"): the full test suite was green at the time, MockMvc integration tests can't see
 * a frontend redirect ProtectedRoute silently reroutes an instructor away from. CI wires this up
 * against a real docker-compose stack (see .github/workflows/ci.yml's e2e job); locally, run the
 * backend + `npm run dev` yourself first (see README) and point BASE_URL at whichever is up.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false, // tests share one backend + database; running serially keeps data isolated by unique emails without needing per-test cleanup
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? 'github' : 'list',
  timeout: 30_000,
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
