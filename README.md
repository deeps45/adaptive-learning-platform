# Adaptive Learning Platform

A full-stack learning platform - Java/Spring Boot REST API, PostgreSQL,
React/TypeScript frontend - with one deliberate differentiator: quiz
questions are scheduled for review using **SM-2**, the actual spaced-repetition
algorithm behind Anki and SuperMemo, not just stored as a percentage score.
Answer a question wrong and it comes back tomorrow; answer it right several
times in a row and the interval between reviews grows. Most quiz-and-score
apps never implement this - it's the one thing here that's genuinely, not
just superficially, different.

**What I'd do differently with more time:** a proper question bank with
tagging/difficulty metadata instead of quizzes as flat lists of questions,
so the "due for review" queue could pull from across a student's whole
enrollment rather than per-quiz; WebSocket-pushed live updates on the
instructor dashboard instead of polling. The honest one from the first pass
- I'd have caught the three `LazyInitializationException`/routing bugs
documented below with a repository-layer test per entity relationship and a
committed E2E suite from the start, instead of finding them one at a time by
manually clicking through the app - is now closed: every one of those three
bugs has a regression test (`CourseIntegrationTest`, and the E2E flow's
explicit routing/rendering assertions), and `frontend/e2e/` runs against a
real backend + Postgres in CI on every push, not just locally by hand.

## Why this exists, not just what it does

Every design decision below was verified two ways: an automated test that
runs in CI, and (for the full user-facing flows) an actual browser session
driving the running app. The second one is what caught three real bugs
the test suite's mocked/transactional boundaries didn't - documented
honestly in [Bugs found by actually running this](#bugs-found-by-actually-running-this-not-hypothetical)
rather than smoothed over, because a project that claims "I tested it" and
then shows the paper trail of what that testing actually caught is worth
more than one where everything suspiciously worked on the first try.

## The differentiator: SM-2 spaced repetition

`SpacedRepetitionService.review()` implements SuperMemo's SM-2 algorithm
(Wozniak, 1987) exactly - not a simplified approximation. Every submitted
quiz answer updates a per-(student, question) `ReviewCard`:

- **Correct** answers: `repetitions` increments, and the interval until the
  next review follows SM-2's own schedule - 1 day, then 6 days, then
  `previous_interval x easinessFactor` for every repetition after that.
- **Incorrect** answers: `repetitions` resets to 0, interval resets to 1 day.
- **`easinessFactor`** (starts at 2.5, floored at 1.3 per SM-2's own spec)
  adapts per card based on the canonical SM-2 formula, so a question a
  specific student finds hard gets reviewed more often than one they find
  easy - even if both are in the same quiz.

Automatically-graded multiple-choice questions don't have SM-2's original
self-assessed 0-5 "quality of recall" input, so that's mapped deterministically:
correct -> 5 (SM-2's "perfect response"), incorrect -> 1 (anything below the
q<3 threshold behaves identically in the formula, so the exact value below
3 doesn't matter). Documented as a real simplification in the code, not
hidden.

11 unit tests in `SpacedRepetitionServiceTest` verify this against SM-2's
own published reference behavior (the exact interval/easiness sequence a
fresh card produces under repeated correct answers is a matter of public
record, not a subjective choice) - including the floor, the reset-on-failure
behavior, and recovery after a mistake.

## Architecture

```
 React (Vite, TypeScript)              Spring Boot 3 / Java 21
┌─────────────────────┐   JWT bearer  ┌──────────────────────────┐
│ Login/Register       │ ────────────▶│ AuthController            │
│ Student dashboard     │              │ CourseController          │
│ Instructor dashboard  │◀── JSON ─────│ QuizController            │
│ Quiz builder          │              │ AttemptController         │
│ Quiz taking            │              │ ProgressController        │
└─────────────────────┘              └───────────┬──────────────┘
                                                    │
                          ┌─────────────────────────┼─────────────────────┐
                          ▼                         ▼                     ▼
                 SpacedRepetitionService   QuizAttemptService     CourseService
                 (pure SM-2, no I/O)       (grading, double-       (RBAC ownership
                                            submit protection)      checks)
                                                    │
                                                    ▼
                                          PostgreSQL (Flyway-migrated)
```

## Fault tolerance and correctness, not just CRUD

- **Double-submission protection**: `QuizAttemptService.submit()` takes a
  `SELECT ... FOR UPDATE` pessimistic lock on the attempt row, so two
  concurrent submissions for the same attempt (a slow-network retry, a
  double-click) serialize at the database - the loser sees the winner's
  already-`SUBMITTED` status and returns the existing result instead of
  double-grading. Verified under an actual race, not just by reading the
  code: `QuizConcurrencyIntegrationTest` fires 8 concurrent HTTP submissions
  at the same attempt, released simultaneously from a shared
  `CountDownLatch`, repeated 5 times (`@RepeatedTest`) so a race that
  doesn't reproduce on every run isn't mistaken for "fixed."
- **RBAC checked as data ownership, not just role**: `@PreAuthorize("hasRole('INSTRUCTOR')")`
  only proves a STUDENT-role JWT is rejected - it says nothing about
  whether one instructor can see or modify *another* instructor's course.
  `CourseService.requireOwnedCourse()` checks the actual row, and
  `RbacIntegrationTest` explicitly tries cross-instructor and
  cross-student access rather than assuming the annotation covers it.
- **A student never sees the answer key**: `QuizDtos.ChoiceView` has no
  `correct` field - verified by a test that asserts the field is *absent*
  from the JSON, not just that the student can't submit it.
- **Testing against real Postgres, not H2**: every integration test runs
  against an ephemeral Postgres container via Testcontainers.
  `SELECT ... FOR UPDATE` locking behaves differently (or isn't
  meaningfully testable at all) against H2's in-memory engine - testing
  against what production actually runs is the point.
- **Rate limiting on auth endpoints**: `RateLimitFilter` throttles
  `/api/auth/login` and `/api/auth/register` to 10 requests/minute per
  client IP (token-bucket via Bucket4j), ahead of JWT parsing in the filter
  chain - a brute-force login attempt shouldn't get a free pass through
  authentication logic before being throttled, and unthrottled registration
  is a cheap way to burn server CPU (`AuthService.register()` does a real,
  deliberately-slow BCrypt hash per call). In-process/per-instance only -
  scaling this horizontally would need shared bucket state (Bucket4j's
  Redis/Hazelcast-backed `ProxyManager`), not implemented here to keep this
  change's scope contained. `RateLimitIntegrationTest` exercises the actual
  429 behavior, not just that the filter is wired in.
- **`/actuator/health`**: exposes liveness for anything that wants to poll
  it (a container orchestrator, an uptime check) without exposing full
  actuator details (`management.endpoint.health.show-details: never`).

## Bugs found by actually running this (not hypothetical)

All 32 backend tests were green before any of this - and the app still had
three real bugs, all only caught by actually clicking through it in a
browser. Documented here because a test suite passing and an app working
are not the same claim.

1. **`LazyInitializationException` on `Quiz.questions`** - `GET /api/quizzes/{id}`
   threw the moment anything read `quiz.getQuestions()` outside the
   (already-closed) Hibernate session the repository call used. Root cause
   of the fix being non-trivial: Hibernate can't `JOIN FETCH` two `List`
   ("bag") collections in one query (`MultipleBagFetchException`) - fixed
   by changing `Quiz.questions` and `Question.choices` from `List` to
   `Set` (`@OrderBy` still preserves order) so both could be fetched
   together.
2. **Same bug, different entity** - `Course.instructor` (lazy `@ManyToOne`)
   threw the same way from `GET /api/courses`, and separately from
   `GET /api/courses/{id}/quizzes` via `Quiz.course`/`listQuizzesForCourse()`
   using a plain (non-eager) query. Both fixed with `JOIN FETCH` repository
   queries, same pattern as #1. Three separate instances of the same root
   cause, in three different code paths - found one at a time, by actually
   navigating the pages that hit each path, not by inspection.
3. **Instructors bounced out of their own "quiz created" redirect** -
   `QuizBuilderPage` sent a newly-authenticated instructor to
   `/quizzes/{id}`, which is a STUDENT-only route (an instructor can
   design a quiz but not "take" it as a graded attempt, matching the
   backend's own `hasRole('STUDENT')` check on starting an attempt) - so
   `ProtectedRoute` immediately redirected them back to their dashboard
   instead of showing the quiz they'd just made. Fixed by redirecting to
   the course page instead, where the new quiz is listed.

None of these were caught by the 32 passing backend tests at the time,
because none of those tests happened to chain "fetch an entity -> let the
transaction end -> read a lazy field" or "click through the redirect after
quiz creation" in the specific way that triggers them. That's the actual
argument for running the app, not just testing it in isolation.

All three now have a regression test that would catch a revert:
`CourseIntegrationTest` asserts on the actual JSON content of
`GET /api/courses` and `GET /api/courses/{id}/quizzes` (not just that the
calls succeed), and `frontend/e2e/full-flow.spec.ts` asserts the instructor
lands back on the course page - not `/instructor` - immediately after
creating a quiz, and that the course page actually renders the instructor's
name rather than hanging on "Loading…".

## RBAC and auth

JWT access (15 min) + refresh (7 day) tokens, BCrypt password hashing,
`STUDENT`/`INSTRUCTOR` roles. A few specific decisions:

- **Refresh tokens are rejected if presented as access tokens.**
  `JwtAuthenticationFilter` checks the token's `type` claim - without this,
  a leaked/logged refresh token would work as a bearer credential against
  every protected endpoint, not just `/api/auth/refresh`.
- **401 vs. 403 are genuinely different responses.** Spring Security's
  default behavior for a stateless filter chain with no `formLogin()`/
  `httpBasic()` configured is to answer *both* "you're not logged in" and
  "you're logged in but not allowed" with 403 - a real, easy-to-miss REST
  API correctness issue, since a client can't distinguish "log in" from
  "this isn't for you" without it. A custom `AuthenticationEntryPoint`
  fixes this; `AuthIntegrationTest` asserts 401 specifically, not "any 4xx."
- **No unused default admin account.** Spring Boot silently creates an
  in-memory user with a random logged password if no `UserDetailsService`
  bean exists - unused here (auth goes through `AuthService`'s own BCrypt
  check, not Spring Security's), so a no-op bean suppresses that instead
  of leaving an unused account with logged credentials sitting around.

## Quickstart

```bash
docker compose up --build
```

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080 (Swagger UI at `/swagger-ui.html`)

### Local development (without Docker)

```bash
# Postgres (or use docker compose up postgres)
docker run -d -p 5432:5432 -e POSTGRES_DB=learning_platform \
  -e POSTGRES_USER=learning -e POSTGRES_PASSWORD=learning postgres:16-alpine

cd backend && mvn spring-boot:run     # http://localhost:8080
cd frontend && npm install && npm run dev   # http://localhost:5173, proxies /api to 8080
```

### Tests

```bash
cd backend && mvn test      # 35 tests: 11 SM-2 unit tests, 24 Testcontainers
                             # integration tests (real Postgres) - needs Docker running
cd frontend && npx vitest run && npx tsc -b

# E2E (real backend + Postgres + frontend, driven with a real browser - see
# frontend/e2e/full-flow.spec.ts). Start the backend and frontend dev server
# first (see "Local development" above), then:
cd frontend && npx playwright install chromium && npm run e2e
```

## Project layout

```
backend/src/main/java/com/learning/platform/
  entity/       JPA entities (User, Course, Quiz, Question, Choice,
                QuizAttempt, AttemptAnswer, ReviewCard)
  repository/   Spring Data repositories, including the JOIN FETCH
                queries the "bugs found" section above is about
  service/      SpacedRepetitionService (SM-2), QuizAttemptService
                (grading + concurrency), CourseService (RBAC ownership)
  security/     JwtService, JwtAuthenticationFilter, RateLimitFilter
  controller/   REST controllers
  dto/          Request/response records
backend/src/test/java/.../
  service/SpacedRepetitionServiceTest.java    11 unit tests vs. SM-2 spec
  integration/  Testcontainers-backed: Auth, Rbac, QuizFlow, QuizConcurrency,
                Course (lazy-loading regressions), RateLimit
frontend/src/
  pages/        One component per route
  api/          Axios client with automatic access-token refresh
  hooks/AuthContext.tsx
frontend/e2e/    Playwright, driven against a real backend + Postgres - see
                 full-flow.spec.ts and playwright.config.ts
```
