import { expect, test } from '@playwright/test'

/**
 * Drives the real app end-to-end against a real backend + Postgres, the way a user actually
 * would - not MockMvc, not a component test with mocked API calls. This is what originally
 * caught two of the three real bugs documented in the README ("Bugs found by actually running
 * this"): the QuizBuilderPage routing bug (an instructor got silently bounced to /instructor
 * after creating a quiz) and the LazyInitializationException on course.instructor (the course
 * page would have failed to render at all). Both are asserted on explicitly below - a regression
 * here is a regression a `mvn test` alone would never see, since MockMvc never renders a page or
 * follows a client-side redirect.
 */
test('instructor creates a course and quiz; student enrolls, takes it, and sees progress', async ({ browser }) => {
  const runId = `${Date.now()}-${Math.floor(Math.random() * 1e6)}`
  const instructorEmail = `instructor-${runId}@example.com`
  const studentEmail = `student-${runId}@example.com`
  const password = 'password123'
  const courseTitle = `E2E Course ${runId}`
  const quizTitle = 'Geography Basics'

  const instructorContext = await browser.newContext()
  const instructorPage = await instructorContext.newPage()
  const studentContext = await browser.newContext()
  const studentPage = await studentContext.newPage()

  let courseId = ''

  await test.step('instructor registers', async () => {
    await instructorPage.goto('/register')
    await instructorPage.locator('#register-fullname').fill('Ines Instructor')
    await instructorPage.locator('#register-email').fill(instructorEmail)
    await instructorPage.locator('#register-password').fill(password)
    await instructorPage.getByText('Instructor', { exact: true }).click()
    await instructorPage.getByRole('button', { name: 'Sign up' }).click()
    // HomePage redirects a logged-in instructor straight to /instructor (see App.tsx/HomePage.tsx).
    await expect(instructorPage).toHaveURL(/\/instructor$/)
  })

  await test.step('instructor creates a course', async () => {
    await instructorPage.getByPlaceholder('Title').fill(courseTitle)
    await instructorPage.getByPlaceholder('Description').fill('An E2E-created course')
    await instructorPage.getByRole('button', { name: 'Create course' }).click()

    const courseLink = instructorPage.getByRole('link', { name: courseTitle })
    await expect(courseLink).toBeVisible()
    const href = await courseLink.getAttribute('href')
    courseId = href!.replace('/courses/', '')
    expect(courseId).toBeTruthy()
  })

  await test.step('instructor creates a quiz and lands back on the course page (regression: this used to bounce to a student-only route)', async () => {
    await instructorPage.goto(`/courses/${courseId}`)
    await instructorPage.getByRole('link', { name: '+ New quiz' }).click()
    await expect(instructorPage).toHaveURL(`/courses/${courseId}/quizzes/new`)

    await instructorPage.getByPlaceholder('Quiz title').fill(quizTitle)
    await instructorPage.getByPlaceholder('Description').fill('Capitals and geography')
    await instructorPage.getByPlaceholder('Question text').fill('What is the capital of France?')

    const choiceInputs = instructorPage.getByPlaceholder(/Choice \d/)
    await choiceInputs.nth(0).fill('Paris') // stays marked correct - QuizBuilderPage defaults choice 0 to correct
    await choiceInputs.nth(1).fill('Berlin')

    await instructorPage.getByRole('button', { name: 'Create quiz' }).click()

    // Regression check: used to navigate to /quizzes/{id} (a STUDENT-only route), which
    // ProtectedRoute silently redirected away from, back to /instructor - dropping the
    // instructor off their own course page with no error shown.
    await expect(instructorPage).toHaveURL(`/courses/${courseId}`)
    await expect(instructorPage.getByRole('link', { name: quizTitle })).toBeVisible()
  })

  await test.step('student registers and views the course', async () => {
    await studentPage.goto('/register')
    await studentPage.locator('#register-fullname').fill('Sam Student')
    await studentPage.locator('#register-email').fill(studentEmail)
    await studentPage.locator('#register-password').fill(password)
    // Role defaults to STUDENT - no radio click needed.
    await studentPage.getByRole('button', { name: 'Sign up' }).click()
    await expect(studentPage).toHaveURL(/\/student$/)

    await studentPage.goto(`/courses/${courseId}`)
    // Regression check: GET /api/courses/{id} used to throw a LazyInitializationException
    // reading course.instructor outside the Hibernate session - this would have rendered a
    // perpetual "Loading…" or a broken page instead of the title and instructor byline.
    await expect(studentPage.getByRole('heading', { name: courseTitle })).toBeVisible()
    await expect(studentPage.getByText('by Ines Instructor')).toBeVisible()
  })

  await test.step('student enrolls and sees the quiz', async () => {
    await studentPage.getByRole('button', { name: 'Enroll in this course' }).click()
    await expect(studentPage.getByRole('link', { name: quizTitle })).toBeVisible()
  })

  await test.step('student takes the quiz and sees a graded result', async () => {
    await studentPage.getByRole('link', { name: quizTitle }).click()
    await expect(studentPage.getByRole('heading', { name: quizTitle })).toBeVisible()
    await studentPage.getByRole('button', { name: 'Start quiz' }).click()

    await studentPage.waitForSelector('input[type=radio]')
    await studentPage.getByText('Paris', { exact: true }).click()
    await studentPage.getByRole('button', { name: 'Submit' }).click()

    await expect(studentPage.getByRole('heading', { name: 'Results' })).toBeVisible()
    await expect(studentPage.getByText('1 / 1 (100%)')).toBeVisible()
  })

  await test.step("student's dashboard reflects the attempt", async () => {
    await studentPage.goto('/student')
    await expect(studentPage.getByText('Quizzes attempted')).toBeVisible()
    await expect(studentPage.locator('p.text-2xl', { hasText: '100%' })).toBeVisible()
  })

  await test.step('instructor analytics dashboard shows the enrolled student', async () => {
    await instructorPage.goto(`/courses/${courseId}/dashboard`)
    await expect(instructorPage.getByText('Sam Student')).toBeVisible()
    await expect(instructorPage.getByText('Enrolled students')).toBeVisible()
  })

  await instructorContext.close()
  await studentContext.close()
})

test('logging in with the wrong password shows an error and does not navigate away', async ({ page }) => {
  const email = `wrongpass-${Date.now()}@example.com`
  await page.goto('/register')
  await page.locator('#register-fullname').fill('Wrong Pass')
  await page.locator('#register-email').fill(email)
  await page.locator('#register-password').fill('correctPassword123')
  await page.getByRole('button', { name: 'Sign up' }).click()
  await expect(page).toHaveURL(/\/student$/)

  await page.getByRole('button', { name: 'Log out' }).click()
  await expect(page).toHaveURL(/\/login$/)

  await page.locator('#login-email').fill(email)
  await page.locator('#login-password').fill('definitelyWrong')
  await page.getByRole('button', { name: 'Log in' }).click()

  await expect(page.getByText('Invalid email or password')).toBeVisible()
  await expect(page).toHaveURL(/\/login$/)
})
