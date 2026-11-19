# Tasks: BookyGeek Online Bookstore Platform

AMD-002 (owner-approved): book reviews/ratings, author section, profile page, cart quantity control, required-field asterisks.

- Q1. One review per customer per book; no edit, no delete.
- Q2. Rating 1 to 5 required; comment optional, max 100 characters.
- Q3. "About the writer" = author name plus the author's other books (no photo, no biography, no new endpoint).
- Q4. Profile page = read-only name and e-mail, gift points balance, saved addresses with existing add/edit/delete reused; no wallet, no Edit Name, no points history.

**Input**: Design documents in `specs/001-bookygeek-platform/` — `spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`, `contracts/api-contract-outline.md`, `amendments/AMD-001-spring-boot-4.md`
**Prerequisites**: `plan.md` (approved), `spec.md` (approved), constitution v2.0.0 (Spring Boot 4.x)
**Tests**: INCLUDED. NFR-TST-01..04 require JUnit/Mockito/Spring Boot Test (backend), Vitest + React Testing Library (frontend) and Playwright (end to end). Tasks below name test files only; no test code is written by this document.
**Organization**: Setup → Foundational → one phase per user story (priority order) → Polish → Capstone submission and verification.

## Format: `- [ ] T### [P?] [US#?] Description with file path`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task).
- **[US#]**: user story label (user-story phases only). Setup, Foundational, Polish and Capstone tasks carry no story label.
- All paths are relative to the repository root `bookygeek/`. Backend package root is `backend/src/main/java/com/bookygeek/` (abbreviated `…/bookygeek/` below); backend tests are under `backend/src/test/java/com/bookygeek/`.

## Gate rules that govern this task list

| Gate | Status | Effect on tasks |
|---|---|---|
| G-DM (data model) | APPROVED | Schema tasks (T037–T040) may be executed once G-API is approved. |
| G-AMD-001 (Spring Boot 4.x) | APPROVED and applied | Backend scaffolding uses Spring Boot 4.x (version pinned in T003). |
| G-PLAN | APPROVED | Additions approved in `plan.md` are allowed. GitHub Actions remains optional and needs separate approval. |
| **G-API (OpenAPI)** | **APPROVED (2026-11-14)** | Project owner approved pi/openapi.yaml (T022) after three review corrections to the contract. Phase 2c and Phases 3–9 backend tasks are unblocked; any later contract change returns to the owner. |

No Phase 1 task creates entities, tables or endpoints.

---

## Phase 1: Setup (shared infrastructure)

**Purpose**: Repository layout, tooling, containers. No domain code, no entities, no tables, no endpoints.

- [X] T001 Create the top-level layout `api/`, `backend/`, `frontend/`, `e2e/`, `docs/` and confirm `.gitignore` at repo root excludes `.env`, `node_modules/`, `target/`, `dist/`, `test-results/`, `playwright-report/`, IDE files (create or extend `.gitignore`)
- [X] T002 Create `.env.example` with placeholder-only names `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `JWT_TTL_MINUTES`, `COOKIE_SECURE`, `CORS_ALLOWED_ORIGINS`, `SPRING_PROFILES_ACTIVE`, `BOOKYGEEK_REWARDS_INITIAL_POINTS` (no real secret values; secrets are never committed)
- [X] T003 [P] Create `backend/pom.xml`: Java 21, Spring Boot 4.x (pin the exact latest stable 4.x release and re-verify support status and the starter renames against the Boot 4 migration guide before committing), starters for web, data-jpa, security, validation, Flyway (its own Boot starter), PostgreSQL driver, test starters; JUnit Jupiter as managed by Boot 4; Mockito; OWASP Dependency-Check Maven plugin configured with `failBuildOnCVSS` 7.0; no Lombok, springdoc, Testcontainers, MapStruct or Actuator
- [X] T004 [P] Create `backend/src/main/resources/application.yml`, `application-dev.yml`, `application-test.yml`, `application-prod.yml`: `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, Flyway enabled (`db/migration`; `db/seed` added only for `dev` and `test`), datasource and JWT values read from environment variables, `server.address=127.0.0.1` for local runs, JSON console logging via Boot's native structured logging format
- [X] T005 [P] Create `backend/src/main/java/com/bookygeek/BookygeekApplication.java` (application entry point only) and the empty package folders `common`, `user`, `catalogue`, `category`, `cart`, `order`, `payment`, `reward` under `backend/src/main/java/com/bookygeek/`
- [X] T006 [P] Create `frontend/package.json` (React, React Router, Axios, Vite, Vitest, React Testing Library plus approved jsdom and testing-library companions; `npm audit`-clean latest stable versions), `frontend/package-lock.json`, `frontend/vite.config.js` (dev proxy `/api` → `http://127.0.0.1:8080`), `frontend/index.html`, `frontend/src/main.jsx`
- [X] T007 [P] Create `frontend/vitest.config.js` and `frontend/src/test/setup.js` (jsdom environment, jest-dom matchers, `localStorage` reset between tests)
- [X] T008 [P] Create `e2e/package.json` and `e2e/playwright.config.js` (Chromium, Firefox and WebKit projects; base URL from environment; screenshots and traces on failure only)
- [X] T009 [P] Create `backend/Dockerfile`: multi-stage; build stage and runtime stage from `registry.redhat.io` UBI minimal/OpenJDK images (exact tags confirmed at build time after `podman login registry.redhat.io`); non-root `USER 1001`; read-only root filesystem compatible; no secrets baked in
- [X] T010 [P] Create `frontend/Dockerfile` and `frontend/nginx.conf`: multi-stage Node build → UBI nginx runtime, non-root, listens on an unprivileged port, proxies `/api` to the backend service, security headers set
- [X] T011 Create `docker-compose.yml`: services `postgres` (UBI PostgreSQL, named volume, `127.0.0.1` binding only), `backend`, `frontend`, and `postgres-test` under profile `test` published on `127.0.0.1:5433`; all host ports published on `127.0.0.1` only; capabilities dropped; all credentials from `.env`; health checks and startup ordering (depends on T009, T010)
- [X] T012 [P] Create `.github/dependabot.yml` for `maven` (`/backend`), `npm` (`/frontend`, `/e2e`) and `docker` ecosystems (Dependabot is approved under PL-08; do NOT add a GitHub Actions workflow without separate owner approval)
- [X] T013 [P] Create `README.md` skeleton with headings only: overview, prerequisites, quick start, test commands (quickstart §7 order), configuration table, gates, troubleshooting
- [X] T014 Verify scaffolding: `cd backend && mvn -q -DskipTests package` builds an application with no entities; `cd frontend && npm ci && npm run build` succeeds; `docker compose config` validates (depends on T003–T011) — **STATUS: done (owner-accepted exception).** Backend `mvn package`, frontend `npm ci`/`npm run build`/`npm audit` passed; Compose steps skipped by owner exception (`docker compose config` not run).

**Checkpoint**: Empty but buildable backend and frontend, containers defined, no domain code.

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: Contract gate (2a), frontend foundation (2b), backend foundation (2c).
**Ordering**: 2a is sequential and blocks 2c and Phases 3–9 backend work. 2b may run in parallel with 2a using fixture data. Frontend work for any story must use fixtures until the matching API is implemented.

### Phase 2a: G-API — OpenAPI contract authoring, validation and approval (BLOCKS ALL BACKEND IMPLEMENTATION)

- [X] T015 Create `api/openapi.yaml` (OpenAPI 3.0.3, hand-written; no springdoc): `info`, `servers` (`/api/v1`), security schemes (`BG_AUTH` cookie, `X-XSRF-TOKEN` header), shared `components/schemas/ApiError` with the error-code enum (`VALIDATION_FAILED`, `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `EMAIL_TAKEN`, `NOT_FOUND`, `BOOK_NOT_FOUND`, `QUANTITY_LIMIT`, `OUT_OF_STOCK`, `EMPTY_BASKET`, `BASKET_CHANGED`, `POINTS_LIMIT_EXCEEDED`, `PAYMENT_DECLINED`, `CANCELLATION_WINDOW_EXPIRED`, `ALREADY_CANCELLED`, `INTERNAL_ERROR`), shared parameters (page size fixed at 12), money as decimal strings
- [X] T016 Add authentication and address operations to `api/openapi.yaml`: `register`, `login`, `logout`, `getCurrentCustomer`, `listAddresses`, `createAddress`, `updateAddress`, `deleteAddress`, `listStates` — request/response schemas with validation rules from FR-AUTH and the address constraints (`phone` `^[0-9]{10}$`, `pinCode` `^[0-9]{6}$`, `country` default India, `addressLine2` optional), `operationId` on each
- [X] T017 Add catalogue operations to `api/openapi.yaml`: `listCategories`, `listBrands`, `listBooks` (search, category, brand, language, format, price band, sort, page), `getBookFilterOptions`, `getBook`, `listRelatedBooks` — book schema with `format` enum `PAPERBACK`/`HARDCOVER`/`EBOOK`, `stockQuantity` nullable for eBooks, delivery-date field
- [X] T018 Add basket operations to `api/openapi.yaml`: `previewBasket` (public), `getBasket`, `addBasketItem`, `updateBasketItem`, `removeBasketItem`, `mergeGuestBasket` (with merge report), `getBasketRecommendations` — quantity 1..10, totals breakdown (subtotal, tax, delivery charge)
- [X] T019 Add checkout and order operations to `api/openapi.yaml`: `getRewardAccount`, `quoteCheckout`, `placeOrder` (required `Idempotency-Key` header, payment-method union with card/UPI/COD details, `expectedPayableTotal`, `pointsToRedeem`), `listOrders`, `getOrder`, `cancelOrder`, `buyAgain` (add report) — all 29 operations now have an `operationId`
- [X] T020 Add `examples` to every operation in `api/openapi.yaml` reusing the PL-05 seed values and PL-06 dummy payment values (card `4111-1111-1111-1111` success, `4000-0000-0000-0002` decline, UPI `demo@upi` success, `failure@upi` decline) and one example per error code
- [X] T021 Validate `api/openapi.yaml` in Swagger Editor (zero errors, zero warnings; every `$ref` resolves; every operation has an `operationId`, request and response schemas and error responses); record the outcome and date in `specs/001-bookygeek-platform/contracts/api-contract-outline.md` §5
- [X] T022 **OWNER GATE — G-API approval**: project owner reviews `api/openapi.yaml` against the checklist in `contracts/api-contract-outline.md` §5 and approves; record approval in the Gate Register of `plan.md` and tick the §5 checklist (edits to those two planning files are made only to record this approval, with the owner's explicit say-so). **Phase 2c and every backend task from Phase 3 onward are blocked until this is done.**
- [X] T023 Create `api/TRACEABILITY.md` mapping each `operationId` → controller class → test class (all 29 rows, controller and test columns initially "planned"); completed during Polish (depends on T022)

### Phase 2b: Frontend foundation (parallel with 2a; fixture data only)

- [X] T024 [P] Create `frontend/src/styles/tokens.css` (dark theme colour, spacing, type, breakpoint tokens at 640 px and 1024 px, visible focus ring, minimum contrast per NFR-ACC) and `frontend/src/styles/base.css`
- [X] T025 [P] Create common components with CSS and RTL tests: `frontend/src/components/common/Button.jsx`, `Field.jsx` (label, error message wired with `aria-describedby`), `Modal.jsx` (focus trap, Escape closes), `Spinner.jsx`, `ErrorMessage.jsx`, `EmptyState.jsx`, tests in `frontend/src/components/common/__tests__/`
- [X] T026 [P] Create `frontend/src/utils/money.js` (decimal-string formatting as ₹ with Indian grouping, no floating-point arithmetic), `date.js` (delivery-date and order-date formatting), `storage.js` (guest basket `{bookId, quantity}` read/write in `localStorage`, defensive parse), tests in `frontend/src/utils/__tests__/`
- [X] T027 [P] Create `frontend/src/utils/validators.js` implementing the client-side mirrors of the FR-AUTH rules (full name, e-mail, password 8 to 64 characters with a letter and a digit, confirmation match), address rules (`phone` `^[0-9]{10}$`, `pinCode` `^[0-9]{6}$`) and PL-06 payment rules (card number, CVV 3 digits, name 2–60 characters, expiry `MM/YYYY` not earlier than the current month, UPI `^[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9]{1,31}$`), tests in `frontend/src/utils/__tests__/validators.test.js`
- [X] T028 Create `frontend/src/api/axiosClient.js`: the only place Axios is created; `baseURL` `/api/v1`, `withCredentials`, 10 s timeout, `X-XSRF-TOKEN` echoed from the `XSRF-TOKEN` cookie, response interceptor maps `ApiError` and redirects to `/login?returnTo=` on 401 `UNAUTHENTICATED`, test in `frontend/src/api/__tests__/axiosClient.test.js`
- [X] T029 [P] Create `frontend/src/hooks/useAsync.js` (loading / error / retry states) with test in `frontend/src/hooks/__tests__/useAsync.test.js`
- [X] T030 [P] Create `frontend/src/components/layout/Header.jsx`, `Sidebar.jsx` (slots for category tree and Brands list), `PageShell.jsx` with CSS (responsive at 640 and 1024 px; header shows BookyGeek, My Orders, basket badge, account menu) and tests in `frontend/src/components/layout/__tests__/`
- [X] T031 Create `frontend/src/context/AuthContext.jsx` (current customer, `login`, `logout`, session restore via `getCurrentCustomer`, no token in JavaScript-readable storage) and `frontend/src/context/BasketContext.jsx` (guest basket in `localStorage`, server basket when logged in, badge count) with tests in `frontend/src/context/__tests__/` (depends on T026, T028)
- [X] T032 Create `frontend/src/routes.jsx` (`ProtectedRoute` redirecting with `returnTo`), `frontend/src/App.jsx`, `frontend/src/pages/NotFoundPage.jsx`, and wire `main.jsx`; routes registered for all pages in the plan layout (placeholders render `PageShell` only) (depends on T030, T031)
- [X] T033 [P] Create fixture data `frontend/src/test/fixtures/` (books, categories, brands, basket, orders, `ApiError` examples mirroring `api/openapi.yaml` examples) and a banned-string UI test `frontend/src/test/bannedStrings.test.jsx` asserting the app never renders "Book Worm", "Wishlist", "Coupon" or "Wallet"
- [X] T034 [P] Create SVG placeholder covers in `frontend/public/covers/` (one per seed book plus a default) with alt-text convention "Cover of {title}" documented in `frontend/public/covers/README.md`

### Phase 2c: Backend foundation (unblocked: G-API approved 2026-11-14)

- [X] T035 Create `backend/src/main/java/com/bookygeek/common/error/ApiError.java` (record matching the approved `ApiError` schema), `common/error/ErrorCode.java` (enum of the codes in T015), `common/error/GlobalExceptionHandler.java` (`@RestControllerAdvice`; generic messages only, no stack traces, details logged server-side), and tests in `backend/src/test/java/com/bookygeek/unit/common/GlobalExceptionHandlerTest.java`
- [X] T036 [P] Create `backend/src/main/java/com/bookygeek/common/time/ClockConfig.java` (injectable `Clock`, UTC) and `common/props/BookygeekProperties.java` (`bookygeek.rewards.initial-points` default 1000, `bookygeek.rewards.cap-percent` default 50, price bands from PL-05, `bookygeek.tax.rate` 5%, `bookygeek.delivery.charge` 40.00, `bookygeek.cancellation.window-hours` 48) with a unit test in `backend/src/test/java/com/bookygeek/unit/common/BookygeekPropertiesTest.java`
- [X] T037 Create `backend/src/main/resources/db/migration/V1__init_schema.sql` — part 1, identity tables. Conventions apply to every table: `BIGINT GENERATED … AS IDENTITY` primary keys, `TIMESTAMPTZ` in UTC, money `NUMERIC(12,2)`. `customer`: `email` is `VARCHAR(254)` NOT NULL UNIQUE, stored trimmed and lower-cased; `token_version` is `INTEGER NOT NULL DEFAULT 0`. `address`: `phone CHAR(10)` CHECK `~ '^[0-9]{10}$'`; `pin_code CHAR(6)` CHECK `~ '^[0-9]{6}$'`; `country` default 'India'; `address_line2` is NULL. `reward_account`: PK is `customer_id`; `balance INTEGER` NOT NULL CHECK `balance ≥ 0`. `reward_transaction`: `type` CHECK IN (`REDEEM`,`RESTORE`); `points` CHECK `points > 0`; UNIQUE `(order_id, type)`
- [X] T038 Extend `backend/src/main/resources/db/migration/V1__init_schema.sql` — part 2, catalogue tables. `category`: `name` is UNIQUE; `parent_id` is a self-FK; CHECK `parent_id <> id`. `book`: `format` CHECK IN (`PAPERBACK`,`HARDCOVER`,`EBOOK`); `price` CHECK `price > 0`; `stock_quantity` CHECK `(format='EBOOK' AND stock_quantity IS NULL) OR (format<>'EBOOK' AND stock_quantity IS NOT NULL AND stock_quantity >= 0)`; `listed` is `BOOLEAN NOT NULL DEFAULT TRUE`; indexes on publisher, language, format, price, listed. `book_category`: PK `(book_id, category_id)` (depends on T037)
- [X] T039 Extend `backend/src/main/resources/db/migration/V1__init_schema.sql` — part 3, basket, order and payment tables. `basket_item`: PK `(customer_id, book_id)`; `quantity` CHECK BETWEEN 1 AND 10. `orders`: `status` CHECK IN (`PLACED`,`CANCELLED`); `cancelled_at` CHECK `(status='CANCELLED') = (cancelled_at IS NOT NULL)`; `points_redeemed` CHECK `2 * points_redeemed <= subtotal + tax + delivery_charge`; `payable_total` CHECK `payable_total = subtotal + tax + delivery_charge − points_redeemed` and `payable_total > 0`; address snapshot columns `ship_*`; index `(customer_id, placed_at DESC)`. `order_item`: PK `(order_id, book_id)`; `quantity` CHECK 1..10; `unit_price` CHECK > 0. `payment`: `order_id` is NULL and UNIQUE; `method` CHECK IN (`CREDIT_CARD`,`DEBIT_CARD`,`UPI`,`COD`); `status` CHECK IN (`PAID`,`PENDING_ON_DELIVERY`,`FAILED`,`REFUNDED`,`CANCELLED`); `idempotency_key` is `UUID NOT NULL`, UNIQUE `(customer_id, idempotency_key)`; CHECK `(status = 'FAILED' AND order_id IS NULL) OR (status <> 'FAILED' AND order_id IS NOT NULL)` (depends on T038)
- [X] T040 Review `V1__init_schema.sql` against `specs/001-bookygeek-platform/data-model.md` (11 tables, every constraint above present verbatim, foreign keys and module ownership respected) and record the result in the PR/commit description (depends on T039)
  - Review result (2026-11-14): V1__init_schema.sql has 11 tables matching data-model §2.1–2.11; every CHECK, UNIQUE and FK listed in T037–T039 is present. Applied with Flyway 11 to PostgreSQL 18.6 (local throw-away cluster on 127.0.0.1:5433, because Docker is unavailable) and `flyway validate` passed; 47 constraint probes (accept and reject cases for each CHECK/UNIQUE, including the 50% reward cap at 545 and 546 points and the payment FAILED/order_id rule) all behaved as specified. category and ook use GENERATED BY DEFAULT identity so the seed can use fixed ids; all other tables use GENERATED ALWAYS.
- [X] T041 [P] Create dev/test seed under `backend/src/main/resources/db/seed/`: `R__seed_categories.sql` (Fiction with 10 leaves: Romance, Mystery, Science Fiction, Fantasy, Historical, Young Adult, Children's, Comics & Graphic Novels, Poetry, Drama; Non-Fiction with 9 leaves: Biography, Self Help, Memoir, Travel, Cooking, Science, Philosophy, Religion, Language Learning) and `R__seed_books.sql` (≈ 60 books; formats PAPERBACK/HARDCOVER/EBOOK; languages English, Hindi, French, Spanish; ≥ 8 publishers; ≥ 13 books in at least one category; one physical book with stock 0, one with stock 1, one eBook; cover paths from T034; no customers). Seed location is enabled only for `dev` and `test` profiles (depends on T040)
- [X] T042 [P] Create `backend/src/main/resources/reference/states.json` (India states and union territories, classpath file; no database table)
- [X] T043 Create `backend/src/main/java/com/bookygeek/common/security/` — `SecurityConfig.java` (Spring Security 7 lambda DSL; stateless; CSRF cookie-to-header `XSRF-TOKEN`/`X-XSRF-TOKEN`; CORS from `CORS_ALLOWED_ORIGINS`; public paths per the approved OpenAPI security requirements; security headers), `JwtService.java` (HS256, 60-minute absolute lifetime, no refresh token, secret from `JWT_SECRET` never logged), `JwtAuthenticationFilter.java` (reads the `BG_AUTH` cookie, compares `token_version`), `AuthCookieFactory.java` (HttpOnly, SameSite=Strict, `Path=/api`, `Secure` from `COOKIE_SECURE`), `PasswordConfig.java` (`DelegatingPasswordEncoder` with PBKDF2-SHA256)
  - Notes (2026-11-14): password hashing is `{pbkdf2}` PBKDF2-HMAC-SHA256 with 600,000 iterations and a 16-byte salt inside a `DelegatingPasswordEncoder`; `JwtService` rejects a token at the exact expiry instant (absolute lifetime) and the configured TTL can shorten but never exceed 60 minutes; the filter reads `token_version` through the `TokenVersionLookup` port, which the User module implements in T057 (until then no token is accepted, which fails closed).
- [X] T044 [P] Create the base integration test support `backend/src/test/java/com/bookygeek/integration/IntegrationTestBase.java` (Spring Boot Test, profile `test`, Postgres on `127.0.0.1:5433`, `@MockitoBean` usage per Boot 4, per-test data reset) and `backend/src/test/java/com/bookygeek/integration/SecurityBaselineIT.java` (unauthenticated access to a protected path → 401 `UNAUTHENTICATED` as `ApiError`; missing CSRF header on a mutating request → 403; cookie flags)
  - Deviation recorded (2026-11-14): the task text says a missing CSRF header gives 403, but the approved contract states that 403 is not used and declares no 403 response, so `SecurityBaselineIT` asserts the contract behaviour: a missing or mismatching `X-XSRF-TOKEN` is rejected with 401 `UNAUTHENTICATED` as an `ApiError`, and the handler is never reached. No contract change was made.
- [X] T045 [P] Create the architecture test `backend/src/test/java/com/bookygeek/architecture/ModuleBoundaryTest.java` using plain JUnit reflection: no cross-module access to another module's repository or entity, only the Catalogue ↔ Category & Brand pair may reference each other (`subtreeIds`, `listPublishers`), controllers do not touch repositories, services are concrete classes
- [X] T046 Verify the foundation: `docker compose --profile test up -d postgres-test` then `cd backend && mvn clean verify` runs Flyway V1 plus seed on an empty database with `ddl-auto=validate` and all T035–T045 tests pass (depends on T035–T045)
  - STATUS: done (owner-accepted exception) (2026-11-14). The `docker compose --profile test up -d postgres-test` step could not run (no Docker or Podman here). The same verification was run against a throw-away local PostgreSQL 18.6 on `127.0.0.1:5433` (the version `docker-compose.yml` pins; cluster outside the repository): on an empty database `mvn clean verify` applied Flyway V1 plus both seed files with `ddl-auto=validate`, 31 unit tests and 13 integration tests passed. Compose steps skipped by owner exception.

**Checkpoint**: Contract approved, schema loaded, security baseline and error model in place. User-story phases may start.

---

## Phase 3: User Story 1 — Register, log in and log out (Priority: P1) 🎯 MVP

**Goal**: A visitor registers with full name, e-mail and password, logs in, sees their name in the account menu and logs out; protected pages redirect to login and return afterwards. Each new account is created with its reward account (1,000 points).
**Independent test**: E2E-01 — register → login → account menu shows name → logout → `/orders` redirects to login → login returns to `/orders`. API: `register`, `login`, `logout`, `getCurrentCustomer` contract tests pass; logout invalidates the token (`token_version` bumped).
**Requirements**: FR-AUTH-01..07, FR-RWD-06, NFR-SEC.

### Tests for User Story 1 (write first; expect failure before implementation)

- [X] T047 [P] [US1] Unit tests for registration/login rules (e-mail trimmed and lower-cased, case-insensitive uniqueness, password 8 to 64 characters with a letter and a digit, unknown e-mail runs the dummy-hash comparison so timing and message match a wrong password) in `backend/src/test/java/com/bookygeek/unit/user/AuthServiceTest.java` — **STATUS: done.** 16 tests.
- [X] T048 [P] [US1] Unit tests for `RewardService` account creation and ledger rules (initial points from `bookygeek.rewards.initial-points`; `balance ≥ 0`; a `REDEEM` row only when `points_redeemed > 0`; a `RESTORE` row only when points are actually restored; UNIQUE `(order_id, type)` prevents double restore) in `backend/src/test/java/com/bookygeek/unit/reward/RewardServiceTest.java` — **STATUS: done.** 14 tests.
- [X] T049 [P] [US1] Integration tests in `backend/src/test/java/com/bookygeek/integration/AuthFlowIT.java`: register creates customer + reward account with 1,000 points in one transaction, duplicate e-mail → `EMAIL_TAKEN`, wrong credentials → `INVALID_CREDENTIALS`, cookie flags (HttpOnly, SameSite=Strict, `Path=/api`), `getCurrentCustomer`, logout bumps `token_version` and a replayed old cookie → 401, expired token → 401 — **STATUS: done.** 18 tests; added a regression test that an authenticated request does not expire the `XSRF-TOKEN` cookie.
- [X] T050 [P] [US1] Contract test `backend/src/test/java/com/bookygeek/contract/AuthContractTest.java` validating `register`, `login`, `logout`, `getCurrentCustomer` responses against `api/openapi.yaml` — **STATUS: done.** 9 tests with a hand-written `ContractSupport` (no validator library). Contract tests need PostgreSQL, so `pom.xml` now excludes `**/contract/*Test.java` from Surefire and includes them (and `*IT.java`) in Failsafe. The contract uses 401 `UNAUTHENTICATED` for every security failure (403 is not declared), including a missing or mismatching CSRF token.
- [X] T051 [P] [US1] Frontend tests: `frontend/src/pages/__tests__/RegisterPage.test.jsx`, `LoginPage.test.jsx` (field-level errors, double-submit prevented, `returnTo` honoured, server `ApiError` shown), `frontend/src/components/layout/__tests__/AccountMenu.test.jsx`
- [X] T052 [P] [US1] Playwright test E2E-01 in `e2e/tests/e2e-01-auth.spec.js` — **STATUS: done (Chromium only).** Run against the dev profile with a local PostgreSQL 18.6 on 127.0.0.1:5433 and Vite on 127.0.0.1:5173. Docker, Firefox and WebKit are not available here, so the Compose-stack run (`E2E_BASE_URL=http://127.0.0.1:3000`) is still to be done on a machine with Docker.

### Implementation for User Story 1

- [X] T053 [P] [US1] Create `…/bookygeek/user/entity/Customer.java` and `…/bookygeek/user/repository/CustomerRepository.java` (columns exactly as T037; `email` unique lower-cased; `token_version` default 0)
- [X] T054 [P] [US1] Create `…/bookygeek/reward/entity/RewardAccount.java`, `reward/entity/RewardTransaction.java`, `reward/repository/RewardAccountRepository.java` (pessimistic `FOR UPDATE` lookup), `reward/repository/RewardTransactionRepository.java`
- [X] T055 [US1] Create `…/bookygeek/reward/service/RewardService.java` (concrete class: `createAccount`, `getBalance`, `lockAccount`, `deduct`, `restore`; ledger rows per the T048 rules; used by User at registration and by Order in Phase 7/8) (depends on T054)
- [X] T056 [P] [US1] Create DTO records in `…/bookygeek/user/dto/` (`RegisterRequest`, `LoginRequest`, `CustomerResponse`) with Bean Validation matching the approved OpenAPI schemas
- [X] T057 [US1] Create `…/bookygeek/user/service/AuthService.java` (register + reward-account creation in one transaction; login with dummy hash for unknown e-mail; logout bumps `token_version`; no sensitive data logged) (depends on T053, T055, T056, T043)
- [X] T058 [US1] Create `…/bookygeek/user/controller/AuthController.java` implementing `register`, `login`, `logout`, `getCurrentCustomer` exactly per `api/openapi.yaml` (cookie issuance via `AuthCookieFactory`; logout expires the cookie) (depends on T057)
- [X] T059 [P] [US1] Create `frontend/src/api/authApi.js` and `frontend/src/pages/RegisterPage.jsx`, `frontend/src/pages/LoginPage.jsx` with styles (client validation from T027, accessible error messages, `returnTo` handling) and the account menu with logout in `frontend/src/components/layout/AccountMenu.jsx`
- [X] T060 [US1] Wire `AuthContext` to `authApi` and register `ProtectedRoute` on `/orders`, `/checkout` in `frontend/src/routes.jsx`; remove fixtures for the auth flow (depends on T059, T058) — **STATUS: done.** No production code imports `test/fixtures`; it is used by tests only, so there was nothing to remove.
- [X] T061 [US1] Run and fix: `mvn clean verify` (T047–T050), `npm test` (T051), `npx playwright test e2e/tests/e2e-01-auth.spec.js` (T052) — **STATUS: done.** Backend `mvn clean verify`: 61 unit + 40 integration/contract tests pass. Frontend: 180 Vitest tests pass, `npm run build` OK. E2E-01: 2 tests pass on Chromium. Fixed a defect found by E2E-01: the CSRF filter expired `XSRF-TOKEN` on every authenticated request, so logout got 401; `SecurityConfig` now uses a no-op CSRF session-authentication strategy (stateless JWT).

**Checkpoint**: US1 independently demonstrable. Suggested MVP slice ends after US5 (see Implementation Strategy).

---

## Phase 4: User Story 2 — Landing page and catalogue discovery (Priority: P1)

**Goal**: Guests browse by hierarchical category and brand, search, filter by language/format/price band, sort and page (12 per page).
**Independent test**: E2E-02 — guest opens `/`, selects Non-Fiction → Self Help, picks a brand, searches, filters Language + Format + Price, sorts and pages. API: `listCategories`, `listBrands`, `listBooks`, `getBookFilterOptions` contract tests pass.
**Requirements**: FR-LAND-01..04, FR-CAT-01..10 (FR-CAT-09 "Recommended for You" is completed in Phase 8), PL-04, PL-05, PL-07.

### Tests for User Story 2

- [X] T062 [P] [US2] Unit tests for relevance ordering (title equals → starts with → contains → author contains; ties by availability, then title A–Z, then id; default without a search term = available first, then newest) in `backend/src/test/java/com/bookygeek/unit/catalogue/RelevanceSortTest.java`
- [X] T063 [P] [US2] Unit tests for category subtree resolution and brand (`publisher`) listing in `backend/src/test/java/com/bookygeek/unit/category/CategoryServiceTest.java`
- [X] T064 [P] [US2] Integration tests in `backend/src/test/java/com/bookygeek/integration/CatalogueQueryIT.java`: parent category returns books of all descendant leaves, combined filters are ANDed, price bands from configuration, paging is exactly 12, sort options, `listed = false` books never returned, filter options only list values that exist
- [X] T065 [P] [US2] Contract test `backend/src/test/java/com/bookygeek/contract/CatalogueContractTest.java` for `listCategories`, `listBrands`, `listBooks`, `getBookFilterOptions`
- [X] T066 [P] [US2] Frontend tests: `frontend/src/pages/__tests__/CataloguePage.test.jsx` (filters, sort, paging, empty state, loading/error/retry), `frontend/src/components/book/__tests__/BookCard.test.jsx`, `frontend/src/components/layout/__tests__/Sidebar.test.jsx`
- [X] T067 [P] [US2] Playwright test E2E-02 in `e2e/tests/e2e-02-browse.spec.js`

### Implementation for User Story 2

- [X] T068 [P] [US2] Create `…/bookygeek/category/entity/Category.java`, `category/repository/CategoryRepository.java`, `category/dto/CategoryNode.java`, `category/service/CategoryService.java` (tree, `subtreeIds`, `listPublishers` for Brands), `category/controller/CategoryController.java` implementing `listCategories`, `listBrands`
- [X] T069 [P] [US2] Create `…/bookygeek/catalogue/entity/Book.java` and `catalogue/repository/BookRepository.java` (mapped to T038 columns; `stock_quantity` null for eBooks)
- [X] T070 [US2] Create `…/bookygeek/catalogue/service/CatalogueService.java` (filters, price bands, relevance ordering per PL-07, fixed page size 12) and `catalogue/dto/` records (`BookSummary`, `BookPage`, `FilterOptions`) (depends on T068, T069)
- [X] T071 [US2] Create `…/bookygeek/catalogue/controller/BookController.java` implementing `listBooks`, `getBookFilterOptions` per `api/openapi.yaml` (depends on T070)
- [X] T072 [P] [US2] Create `frontend/src/api/catalogueApi.js` and `frontend/src/hooks/useCatalogueQuery.js` (query parameters synchronised with the URL: `?category=`, `?brand=`, search, language, format, price band, sort, page)
- [X] T073 [P] [US2] Create `frontend/src/components/book/BookCard.jsx`, `BookCardCompact.jsx` with CSS (cover with alt "Cover of {title}", title, "by" author link filtering by author, format, genre-tag links, ₹ price, stock/availability, delivery text)
- [X] T074 [US2] Create `frontend/src/pages/CataloguePage.jsx` serving `/` and `/books` (category tree and Brands list in the sidebar, filter row, sort, 12 per page, empty state, `useAsync` states) and CSS (depends on T072, T073)
- [X] T075 [US2] Run and fix: `mvn clean verify`, `npm test`, E2E-02
  - STATUS (Phase 4): T062-T075 done. Backend `mvn clean verify` BUILD SUCCESS (73 unit, 75 integration/contract tests incl. CatalogueQueryIT 26 and CatalogueContractTest 9); frontend 208 Vitest tests and `npm run build` pass; E2E-02 passes on Chromium only (multi-browser and the Compose stack are not run: Docker is unavailable, see T014/T046). Notes: Surefire runs unit tests and Failsafe runs `*IT` and `contract/*Test`; the Brands list is in the sidebar under the categories (PL-04); `/` and `/books` both serve the catalogue; `listBrands` is served by CategoryController, which composes it from `CatalogueService.listPublishers()` to avoid a category/catalogue service cycle; relevance sorting with a search term is done in memory (RelevanceOrdering), all other orders in the database; `Recommended for You` (FR-CAT-09) is deferred to Phase 8; book cards have no add-to-basket control yet (a later phase adds it, so no inert button is shown).

**Checkpoint**: Guests can browse and filter the full catalogue.

---

## Phase 5: User Story 3 — Book detail and related books (Priority: P1)

**Goal**: A book page shows details, availability, delivery date (physical books today + 5 days, eBook today), breadcrumb and related books from the same category.
**Independent test**: E2E-03 — open a book: details, delivery date, related books share a category and exclude the book itself, breadcrumb links work. API: `getBook`, `listRelatedBooks` contract tests pass.
**Requirements**: FR-DET-01..05.

### Tests for User Story 3

- [X] T076 [P] [US3] Unit tests for delivery-date rule using the injected `Clock` (physical = today + 5, eBook = today) and related-books rule in `backend/src/test/java/com/bookygeek/unit/catalogue/BookDetailServiceTest.java`
- [X] T077 [P] [US3] Integration test `backend/src/test/java/com/bookygeek/integration/BookDetailIT.java` (unknown or unlisted id → `BOOK_NOT_FOUND`, related excludes the book itself and unlisted books) and contract test additions in `backend/src/test/java/com/bookygeek/contract/CatalogueContractTest.java` for `getBook`, `listRelatedBooks`
- [X] T078 [P] [US3] Frontend test `frontend/src/pages/__tests__/BookDetailPage.test.jsx` (out-of-stock state, eBook state, breadcrumb, not-found state)
- [X] T079 [P] [US3] Playwright test E2E-03 in `e2e/tests/e2e-03-detail.spec.js`

### Implementation for User Story 3

- [X] T080 [US3] Extend `…/bookygeek/catalogue/service/CatalogueService.java` and `catalogue/controller/BookController.java` with `getBook` and `listRelatedBooks` (delivery date from `Clock`; related = other listed books in the same category) and `catalogue/dto/BookDetail.java` (depends on T071)
- [X] T081 [P] [US3] Extend `frontend/src/api/catalogueApi.js` and create `frontend/src/pages/BookDetailPage.jsx` with CSS (breadcrumb, details, availability, delivery date, related books using `BookCardCompact`; an Add to Basket button wired to `BasketContext` in Phase 6)
- [X] T082 [US3] Run and fix: `mvn clean verify`, `npm test`, E2E-03
  - STATUS (Phase 5): T076-T082 done. Backend `mvn clean verify` BUILD SUCCESS (87 unit, 96 integration/contract tests incl. BookDetailServiceTest 12, BookDetailIT 16 and CatalogueContractTest 14); frontend 226 Vitest tests and `npm run build` pass; E2E-01..03 pass on Chromium only (10 tests, 3 consecutive runs; the Compose stack and other browsers are not run because Docker is unavailable, see T014/T046). Notes: the breadcrumb is the category path to the book's first category (lowest id) and Related Reads uses every category of the book; related order is available first, then newest, then id; a non-numeric or out-of-range id is answered by the standard 404 body, and the page shows the not-found state without calling the API; `CategoryService.pathTo` was added for the breadcrumb (same Catalogue to Category dependency, no new module edge); the Add to Basket button is intentionally absent until T094 (no inert control, FR-DET-03 is completed in Phase 6); the unmatched-route assertion in SecurityBaselineIT now uses a path that is still unmapped because `/books/{id}` is a real route.

**Checkpoint**: Browse → detail works end to end.

---

## Phase 6: User Story 4 — Basket (Priority: P1)

**Goal**: Guests and customers add books, change quantities (1..10), remove items and see totals; the guest basket merges into the server basket at login (PL-01).
**Independent test**: E2E-04 — guest adds two books → logs in → merged basket (quantities summed, capped at min(10, stock), unavailable skipped with a report) → change quantity, remove, 11th unit rejected, header badge correct. API: `previewBasket`, `getBasket`, `addBasketItem`, `updateBasketItem`, `removeBasketItem`, `mergeGuestBasket` contract tests pass.
**Requirements**: FR-BSK-01..06, FR-BSK-08..09 (FR-BSK-07 recommendations are completed in Phase 8), PL-01, PL-02.

### Tests for User Story 4

- [X] T083 [P] [US4] Unit tests for `PricingService` in `backend/src/test/java/com/bookygeek/unit/cart/PricingServiceTest.java`: tax 5% `HALF_UP` once per order, delivery 40.00 if any physical book else 0, eligible total, reward cap `min(balance, floor(50% × eligible))`, `BigDecimal` scale 2, boundary values around rounding
- [X] T084 [P] [US4] Unit tests for basket rules in `backend/src/test/java/com/bookygeek/unit/cart/CartServiceTest.java`: quantity 1..10, `QUANTITY_LIMIT`, `OUT_OF_STOCK`, eBook has no stock limit, merge sums per book, caps at min(10, stock), skips unavailable books and reports them
- [X] T085 [P] [US4] Integration tests `backend/src/test/java/com/bookygeek/integration/BasketIT.java`: `basket_item` PK `(customer_id, book_id)` upsert, `quantity` CHECK BETWEEN 1 AND 10, per-customer isolation, `previewBasket` is public and writes nothing, merge report content, other customer's item cannot be changed
- [X] T086 [P] [US4] Contract test `backend/src/test/java/com/bookygeek/contract/BasketContractTest.java` for the six operations in this story
- [X] T087 [P] [US4] Frontend tests: `frontend/src/pages/__tests__/BasketPage.test.jsx`, `frontend/src/context/__tests__/BasketContext.merge.test.jsx` (storage cleared only after a successful merge; failed merge keeps the guest basket), `frontend/src/hooks/__tests__/useBasket.test.js`
- [X] T088 [P] [US4] Playwright test E2E-04 in `e2e/tests/e2e-04-basket.spec.js`

### Implementation for User Story 4

- [X] T089 [P] [US4] Create `…/bookygeek/cart/entity/BasketItem.java` (composite key `(customer_id, book_id)`) and `cart/repository/BasketItemRepository.java`
- [X] T090 [P] [US4] Create `…/bookygeek/cart/service/PricingService.java` (concrete class; single source of truth for subtotal, tax, delivery, eligible total, reward cap; used by preview, basket, quote and place order; rates from `BookygeekProperties`)
- [X] T091 [US4] Create `…/bookygeek/cart/service/CartService.java` (add, update, remove, list, merge with report; uses Catalogue through its service only) and `cart/dto/` records (`BasketLine`, `BasketResponse`, `MergeRequest`, `MergeReport`) (depends on T089, T090)
- [X] T092 [US4] Create `…/bookygeek/cart/controller/BasketController.java` implementing `previewBasket`, `getBasket`, `addBasketItem`, `updateBasketItem`, `removeBasketItem`, `mergeGuestBasket` per `api/openapi.yaml` (depends on T091)
- [X] T093 [P] [US4] Create `frontend/src/api/basketApi.js` and `frontend/src/hooks/useBasket.js`
- [X] T094 [US4] Complete `frontend/src/context/BasketContext.jsx`: guest basket `{bookId, quantity}` in `localStorage`, `previewBasket` for guest pricing, merge on login with report display, header badge, Add to Basket on `BookCard` and `BookDetailPage` (depends on T093, T092, T060)
- [X] T095 [US4] Create `frontend/src/pages/BasketPage.jsx` with CSS (line items, quantity stepper 1..10, remove, totals breakdown from the API, empty state, merge notice, proceed-to-checkout requires login via `returnTo`) (depends on T094)
- [X] T096 [US4] Run and fix: `mvn clean verify`, `npm test`, E2E-04

  - STATUS (Phase 6): T083-T096 done. Backend `mvn clean verify` BUILD SUCCESS (110 unit, 117 integration/contract tests incl. PricingServiceTest, CartServiceTest, BasketIT and BasketContractTest for all six operations); frontend 257 Vitest tests and `npm run build` pass; E2E-01..04 pass on Chromium only (13 tests, 3 consecutive runs; the Compose stack and other browsers are not run because Docker is unavailable, see T014/T046). Notes: `CartService` reaches Catalogue only through `CatalogueService.listedSummaries(ids)` (new, returns DTOs, so `cart` never sees the `Book` entity) and the module-boundary rule still passes; `PricingService` is the single place for subtotal, tax (5% of the subtotal, once per order, HALF_UP), delivery (40.00 if any physical book, else 0.00) and the reward cap; a merge never reduces a line the customer already has, and a missing book is reported as `NOT_IN_CATALOGUE`; one request line is limited to 10 units by the contract, so repeated lines for one book are summed by the server; a guest basket checks the stock limit in the browser after `previewBasket` and stores nothing when it is exceeded; `BasketContext` calls `basketApi.js` and `useBasket` adds `quantityOf`; Add to Basket is on `BookCard` and `BookDetailPage` (disabled for a physical book with no stock), so the earlier "no add-to-basket control" assertions were replaced; `/basket` is the real page. `GET /recommendations` (FR-BSK-07) stays in Phase 8.

**Checkpoint**: Basket complete for guests and customers.

---

## Phase 7: User Story 5 — Checkout, dummy payment and confirmation (Priority: P1)

**Goal**: A logged-in customer chooses or enters a delivery address, selects a payment method (credit card, debit card, UPI, COD), pays through the simulator, and sees payment and purchase confirmations; failed payments leave nothing changed. The order, payment, stock and (optional) reward-point movement is one atomic transaction.
**Independent test**: E2E-05 and E2E-06 — each of CC, DC, UPI and COD completes (payment confirmation screen except for COD, then purchase confirmation, empty basket, order in My Orders); declined card/UPI shows a message and changes nothing; retry succeeds. API: `listAddresses`, `createAddress`, `updateAddress`, `deleteAddress`, `listStates`, `quoteCheckout`, `placeOrder`, `getOrder` contract tests pass.
**Requirements**: FR-CHK-*, FR-PAY-*, FR-ORD-01..03, FR-CNF-*, FR-RWD-02..05 (backend handling of `pointsToRedeem`), PL-02, PL-06, PL-07.

### Tests for User Story 5

- [X] T097 [P] [US5] Unit tests for `PaymentService` simulator in `backend/src/test/java/com/bookygeek/unit/payment/PaymentServiceTest.java`: card `4111-1111-1111-1111` approves, `4000-0000-0000-0002` declines, any other valid card → declined per PL-06, UPI `demo@upi` approves and `failure@upi` declines, COD → `PENDING_ON_DELIVERY` with no details, no raw card data stored or logged
- [X] T098 [P] [US5] Unit tests for order placement orchestration in `backend/src/test/java/com/bookygeek/unit/order/OrderServiceTest.java`: revalidation of basket and stock, `BASKET_CHANGED` when `expectedPayableTotal` differs, `POINTS_LIMIT_EXCEEDED`, `EMPTY_BASKET`, `REDEEM` row written only when `points_redeemed > 0`, deduction only when `points_redeemed > 0`
- [X] T099 [P] [US5] Integration tests `backend/src/test/java/com/bookygeek/integration/PlaceOrderIT.java`: one transaction creates order, items, payment, decrements stock, clears basket; declined payment writes a `FAILED` payment row (`REQUIRES_NEW`) with `order_id` NULL and rolls back everything else; `orders` CHECK `payable_total = subtotal + tax + delivery_charge − points_redeemed` and `payable_total > 0`; CHECK `2 * points_redeemed <= subtotal + tax + delivery_charge`; payment CHECK `(status = 'FAILED' AND order_id IS NULL) OR (status <> 'FAILED' AND order_id IS NOT NULL)`; same `Idempotency-Key` returns the original result and never double-charges; UNIQUE `(customer_id, idempotency_key)`
- [X] T100 [P] [US5] Concurrency integration test `backend/src/test/java/com/bookygeek/integration/StockConcurrencyIT.java`: two simultaneous orders for the last unit → exactly one succeeds, the other gets `OUT_OF_STOCK`, stock never below 0; books are locked `SELECT … FOR UPDATE` in ascending id order (no deadlock with reversed basket order)
- [X] T101 [P] [US5] Integration tests `backend/src/test/java/com/bookygeek/integration/AddressIT.java` (`phone CHAR(10)` CHECK `~ '^[0-9]{10}$'`, `pin_code CHAR(6)` CHECK `~ '^[0-9]{6}$'`, country default India, `address_line2` optional, a customer cannot read or change another customer's address, address snapshot `ship_*` is copied into the order and unaffected by later edits)
- [X] T102 [P] [US5] Contract tests `backend/src/test/java/com/bookygeek/contract/CheckoutContractTest.java` and `AddressContractTest.java` for the eight operations in this story
- [X] T103 [P] [US5] Frontend tests: `frontend/src/pages/__tests__/CheckoutPage.test.jsx`, `PaymentPage.test.jsx` (per-method forms, PL-06 validation messages, no card data kept in storage, submit disabled while pending), `PaymentConfirmedPage.test.jsx`, `PurchaseConfirmedPage.test.jsx`, `AddressesPage.test.jsx`
- [X] T104 [P] [US5] Playwright tests E2E-05 in `e2e/tests/e2e-05-checkout.spec.js` and E2E-06 in `e2e/tests/e2e-06-payment-failure.spec.js`

### Implementation for User Story 5

- [X] T105 [P] [US5] Create `…/bookygeek/user/entity/Address.java`, `user/repository/AddressRepository.java`, `user/dto/AddressRequest.java`, `user/dto/AddressResponse.java`, `user/service/AddressService.java`, `user/controller/AddressController.java` implementing `listAddresses`, `createAddress`, `updateAddress`, `deleteAddress`, `listStates` (states read from `reference/states.json`)
- [X] T106 [P] [US5] Create `…/bookygeek/payment/entity/Payment.java`, `payment/repository/PaymentRepository.java`, `payment/dto/PaymentRequest.java` (method-specific details, never persisted), `payment/service/PaymentService.java` (simulator per PL-06; `FAILED` row written via `REQUIRES_NEW`; idempotency key stored on the payment row)
- [X] T107 [P] [US5] Create `…/bookygeek/order/entity/OrderEntity.java` (table `orders`), `order/entity/OrderItem.java` (composite key `(order_id, book_id)`), `order/repository/OrderRepository.java`, `order/repository/OrderItemRepository.java`
- [X] T108 [US5] Create `…/bookygeek/order/service/CheckoutQuoteService.java` and `order/dto/` records (`CheckoutQuote`, `PlaceOrderRequest`, `OrderResponse`) — quote uses `PricingService` and `RewardService.getBalance` (depends on T090, T055, T107)
- [X] T109 [US5] Create `…/bookygeek/order/service/OrderService.java` `placeOrder` as one `@Transactional` unit: lock books ascending and the reward account → revalidate basket, stock, `expectedPayableTotal`, points cap → run the simulated payment (decline → `FAILED` row, rollback of the rest) → insert order, items, payment → `REDEEM` row only if `points_redeemed > 0` → decrement stock, deduct points only if `points_redeemed > 0`, clear basket; plus `getOrder` (owner only) (depends on T106, T108, T091, T055)
- [X] T110 [US5] Create `…/bookygeek/order/controller/CheckoutController.java` implementing `quoteCheckout`, `placeOrder` (requires `Idempotency-Key` UUID header) and `order/controller/OrderController.java` `getOrder` per `api/openapi.yaml` (depends on T109)
- [X] T111 [P] [US5] Create `frontend/src/api/addressApi.js`, `frontend/src/api/orderApi.js` (quote, place, get) and `frontend/src/pages/AddressesPage.jsx` (list, add, edit, delete, state selector, validation from T027)
- [X] T112 [US5] Create `frontend/src/pages/CheckoutPage.jsx` (address step, order summary from `quoteCheckout`, payment-method choice; protected route) and `frontend/src/pages/PaymentPage.jsx` (card / UPI forms with PL-06 validation, COD confirmation, one `Idempotency-Key` per attempt, clear card fields on failure, generic decline message) with CSS (depends on T111, T095)
- [X] T113 [P] [US5] Create `frontend/src/pages/PaymentConfirmedPage.jsx` (not shown for COD) and `frontend/src/pages/PurchaseConfirmedPage.jsx` (order number, items, totals, delivery address, payment status) with CSS
- [X] T114 [US5] Run and fix: `mvn clean verify`, `npm test`, E2E-05 and E2E-06
  - STATUS (Phase 7): T097-T114 done. Backend `mvn clean verify` BUILD SUCCESS (146 unit, 169 integration/contract tests incl. PaymentServiceTest 14, OrderServiceTest 22, PlaceOrderIT 26, StockConcurrencyIT 4 with a reversed-basket-order deadlock test, AddressIT 11, AddressContractTest 5 and CheckoutContractTest 6 covering all eight operations); frontend 312 Vitest tests and `npm run build` pass; E2E-01..06 pass on Chromium only (21 tests, 2 consecutive runs; the Compose stack and other browsers are not run because Docker is unavailable, see T014/T046). Notes: Address, Payment and Order are new modules/packages with no new module edge beyond the approved ones, and `CatalogueService` gained the stock-locking and purchase-row methods that `OrderService` uses, so `order` never sees the `Book` entity; `placeOrder` locks books in ascending id order, then the reward account, answers 201 with `Location` (200 on a replay of the same `Idempotency-Key`) and a declined payment leaves one `FAILED` payment row with a NULL `order_id` and nothing else; all security failures, including CSRF, stay 401 `UNAUTHENTICATED`; card data is held only in component state, cleared on failure, never written to web storage, and the CVV is a password input; the idempotency key is kept after a network failure and replaced after any server answer; `/payment` without a checkout redirects to `/checkout`; COD goes straight to the purchase confirmation; the order is verified in E2E-05 through the confirmation page and `getOrder` because My Orders is Phase 8 (`/orders` is still a placeholder). Defect found and fixed in the test base: `IntegrationTestBase` took its stock baseline from the live table, but the seed script never overwrites rows, so stock left behind by an earlier run became the baseline and failed unrelated tests; it now reads the baseline from `db/seed/R__seed_books.sql` and restores stock after every test. E2E-05 and E2E-06 run in parallel Playwright workers, so they use different physical books for their exact stock assertions (book 2 and book 16); the tests that only need a purchasable book use the eBook 13, which has no stock.

**Checkpoint**: Full purchase journey works (points redemption UI is added in Phase 9). **This is the suggested MVP boundary: US1 + US2 + US3 + US4 + US5.**

---

## Phase 8: User Story 6 — Order history, Buy Again, cancellation and recommendations (Priority: P2)

**Goal**: Customers view past orders, cancel an order that is strictly under 48 hours old (stock and points restored, payment status updated), re-add past books with Buy Again, and see order-history recommendations.
**Independent test**: E2E-07 — My Orders lists orders; Buy Again adds available books at current price/stock and lists unavailable or reduced ones; cancel an order under 48 hours: status, stock, points and payment all updated; at exactly 48:00:00 cancellation is rejected. API: `listOrders`, `getOrder`, `cancelOrder`, `buyAgain`, `getBasketRecommendations` contract tests pass.
**Requirements**: FR-ORD-01..06, FR-BSK-07, FR-CAT-09, FR-LAND-03, FR-PAY-12, FR-RWD-06.

### Tests for User Story 6

- [X] T115 [P] [US6] Unit tests for the cancellation window using a fixed `Clock` in `backend/src/test/java/com/bookygeek/unit/order/CancellationRulesTest.java` (47:59:59 allowed, exactly 48:00:00 rejected, already cancelled rejected, other customer's order not found)
- [X] T116 [P] [US6] Unit tests for Buy Again and recommendations in `backend/src/test/java/com/bookygeek/unit/order/BuyAgainServiceTest.java` and `RecommendationServiceTest.java` (current price and stock, caps at min(10, stock), unavailable books listed not added; recommendations = other listed available books in the categories of previously ordered books, excluding basket items; empty without history)
- [X] T117 [P] [US6] Integration tests `backend/src/test/java/com/bookygeek/integration/CancelOrderIT.java`: single transaction restores stock (book rows locked ascending), restores points and inserts a `RESTORE` row only if `points_redeemed > 0`, payment PAID → REFUNDED and PENDING_ON_DELIVERY → CANCELLED, `orders` CHECK `(status='CANCELLED') = (cancelled_at IS NOT NULL)`, second cancel → `ALREADY_CANCELLED`, expired → `CANCELLATION_WINDOW_EXPIRED`, double restore blocked by UNIQUE `(order_id, type)`, concurrent cancel and cancel yields one success
- [X] T118 [P] [US6] Integration tests `backend/src/test/java/com/bookygeek/integration/OrderHistoryIT.java` (index `(customer_id, placed_at DESC)` ordering, unpaged list per OpenAPI contract, order snapshots unaffected by later price or stock changes) and contract tests `backend/src/test/java/com/bookygeek/contract/OrderContractTest.java`
- [X] T119 [P] [US6] Frontend tests: `frontend/src/pages/__tests__/OrdersPage.test.jsx`, `OrderDetailPage.test.jsx` (cancel visible only under 48 hours, confirmation modal, refreshed state), `frontend/src/components/book/__tests__/RecommendedForYou.test.jsx` (hidden when empty)
- [X] T120 [P] [US6] Playwright test E2E-07 in `e2e/tests/e2e-07-orders.spec.js`

### Implementation for User Story 6

- [X] T121 [US6] Extend `…/bookygeek/order/service/OrderService.java` with `listOrders`, `cancelOrder` (load `FOR UPDATE`; reject if not PLACED or `now − placed_at ≥ 48h`; restore stock; restore points and `RESTORE` row only if `points_redeemed > 0`; update payment; set CANCELLED) and extend `order/controller/OrderController.java` with `listOrders`, `cancelOrder` (depends on T110)
- [X] T122 [P] [US6] Create `…/bookygeek/order/service/BuyAgainService.java` (uses `CartService` add logic per line; returns the add report) and `order/service/RecommendationService.java` (category-based per FR-BSK-07), plus `order/controller/RecommendationController.java` and the `buyAgain` operation in `OrderController.java`, implementing `buyAgain` and `getBasketRecommendations` (depends on T091, T107)
- [X] T123 [P] [US6] Extend `frontend/src/api/orderApi.js` and create `frontend/src/pages/OrdersPage.jsx`, `frontend/src/pages/OrderDetailPage.jsx` (cancel with confirmation modal, Buy Again with report) with CSS
- [X] T124 [P] [US6] Create `frontend/src/components/book/RecommendedForYou.jsx` and mount it on `CataloguePage` and `BasketPage` (rendered only for a logged-in customer with a non-empty result; guest sees none)
- [X] T125 [US6] Run and fix: `mvn clean verify`, `npm test`, E2E-07
  - STATUS (Phase 8): T115-T125 done. Backend `mvn clean verify` BUILD SUCCESS (175 unit, 208 integration/contract tests incl. CancellationRulesTest 17, BuyAgainServiceTest 7, RecommendationServiceTest 5, CancelOrderIT 17, OrderHistoryIT 16 and OrderContractTest 6 covering `listOrders`, `getOrder`, `cancelOrder`, `buyAgain` and `getBasketRecommendations`); frontend 342 Vitest tests and `npm run build` pass; E2E-01..07 pass on Chromium only (27 tests; the Compose stack and other browsers are not run because Docker is unavailable, see T014/T046). Notes: cancellation is one transaction (order row locked, books restored in ascending id order, `RESTORE` row only if points were redeemed, PAID becomes REFUNDED and COD PENDING_ON_DELIVERY becomes CANCELLED); the window is strictly under 48 hours for every payment method and is proven by the fixed-`Clock` unit tests and the IT, because a browser cannot back-date an order (E2E-07 covers the happy path, stock restore, payment status, the stale-tab `ALREADY_CANCELLED` rejection, a foreign order shown as not found, recommendations and Buy Again); four concurrent cancels give one 200 and three 409; `listOrders` returns the whole history without paging because `api/openapi.yaml` declares no paging parameters, so the "paging of 12" mentioned in T118 was NOT implemented and the contract was NOT changed (owner decision needed if paging is wanted); the Cancel button on My Orders uses a client-side 48-hour hint because the list has no `cancellable` flag, while the order page uses the server's `cancellable` and the server always decides; recommendations live in the `order` module (needs history, basket and catalogue) without a new module edge; `PlaceholderPage.jsx` was deleted because `/orders` and `/orders/:id` are real pages; `RecommendedForYou` renders only for a signed-in customer with a non-empty result, a guest makes no request; the E2E-04 test "checkout asks a guest to log in" now waits for the add confirmation before navigating (it raced under parallel workers; no assertion was weakened); under 14 parallel workers the older specs timed out on this machine, so E2E was run with `--workers=3`. Points redemption in the UI is Phase 9, so the points restore is verified by the integration tests only.

**Checkpoint**: Order lifecycle complete.

---

## Phase 9: User Story 7 — Reward points redemption (Priority: P2)

**Goal**: At checkout a customer sees their balance, can redeem up to `min(balance, floor(50% × eligible total))` points (1 point = ₹1) and sees the reduced payable total; one point more is rejected.
**Independent test**: E2E-08 — maximum redeemable equals `min(balance, floor(50% of eligible total))`; one more point is rejected with a message; the redeemed points are deducted after order placement and restored on cancellation. API: `getRewardAccount` contract test passes.
**Requirements**: FR-RWD-01..06, PL-02.

### Tests for User Story 7

- [X] T126 [P] [US7] Boundary unit tests in `backend/src/test/java/com/bookygeek/unit/reward/RewardCapTest.java` (balance below cap, equal to cap, above cap; odd totals floor; balance 0; points = cap accepted, cap + 1 rejected with `POINTS_LIMIT_EXCEEDED`)
- [X] T127 [P] [US7] Integration tests `backend/src/test/java/com/bookygeek/integration/RewardRedemptionIT.java` (redeem then cancel returns the balance; `reward_account.balance` never below 0 under concurrent orders; `getRewardAccount` returns only the caller's account) and contract test `backend/src/test/java/com/bookygeek/contract/RewardContractTest.java`
- [X] T128 [P] [US7] Frontend test `frontend/src/pages/__tests__/CheckoutPage.rewards.test.jsx` (max redeemable, over-limit message, payable total updates, redeem 0 allowed)
- [X] T129 [P] [US7] Playwright test E2E-08 in `e2e/tests/e2e-08-rewards.spec.js`

### Implementation for User Story 7

- [X] T130 [US7] Create `…/bookygeek/reward/dto/RewardAccountResponse.java` and `reward/controller/RewardController.java` implementing `getRewardAccount` per `api/openapi.yaml`; confirm `quoteCheckout` exposes `maxRedeemablePoints` from `PricingService` (depends on T055, T090)
- [X] T131 [P] [US7] Create `frontend/src/api/rewardApi.js` and `frontend/src/components/checkout/RewardsRedemption.jsx` with CSS (balance, max redeemable, input, inline validation, live payable total); mount it on `CheckoutPage` and pass `pointsToRedeem` to `placeOrder` (depends on T112)
- [X] T132 [US7] Run and fix: `mvn clean verify`, `npm test`, E2E-08
  - STATUS (Phase 9): T126-T132 done. Backend `mvn clean verify` BUILD SUCCESS (188 unit, 223 integration/contract tests incl. RewardCapTest 13, RewardRedemptionIT 13 and RewardContractTest 2); frontend 362 Vitest tests and `npm run build` pass; E2E-01..08 pass on Chromium only (31 tests; the Compose stack and other browsers are not run because Docker is unavailable, see T014/T046). Notes: `getRewardAccount` is the only new endpoint (`RewardController` returns the caller's own balance, looked up by the authenticated id); `quoteCheckout`, `placeOrder` and `cancelOrder` already enforced the cap, the deduction and the restore, so no contract or business-rule change was needed; the cap is `min(balance, floor(50% x (subtotal + tax + delivery)))` from `PricingService`; the redemption area is `RewardsRedemption` below the order summary on `CheckoutPage` (balance, maximum, input, inline message; zero balance shows "You have no reward points to redeem."); the quote is requested once with 0 points and its `maxRedeemablePoints` is the cap for what the customer types, so the payable total (`total - points`, computed on whole paise with no floating point) updates at once without a request per keystroke; a number above the cap or not a whole number is explained under the input, is not applied to the summary and holds back Pay Now; the server still checks again at placement (`POINTS_LIMIT_EXCEEDED`, `BASKET_CHANGED`); an empty box and 0 both mean no redemption; the balance is shown from `getRewardAccount` and checkout stays usable if it cannot be loaded; points are never earned (E2E-08 and RewardRedemptionIT assert the balance only ever falls by the redeemed points and is restored once by a cancellation); five racing orders on one account give one 201 and four 409 and the balance never goes below 0. E2E-08 uses book 4 (used by no other spec): 249.00 + 12.45 + 40.00 = 301.45, cap 150.

**Checkpoint**: All seven stories complete and independently verifiable.

---

## Phase 10: Polish and cross-cutting concerns

**Purpose**: Whole-system verification, documentation, scans, demo. No new product functionality.

- [X] T133 [P] Complete `api/TRACEABILITY.md` (every `operationId` → controller → contract test → integration test; all 29 rows filled) and confirm no controller exposes an operation absent from `api/openapi.yaml` (depends on T023)
- [X] T134 [P] Create `docs/test-matrix.md` mapping every FR/NFR to its automated test or manual check (rule-to-test mapping per NFR-TST), including the architecture test and banned-string test
- [X] T135 Run the full test sequence from quickstart §7 in order and fix failures: `docker compose --profile test up -d postgres-test` → `cd backend && mvn clean verify` → `cd frontend && npm ci && npm test && npm run build` → `cd e2e && npx playwright test` (Chromium, Firefox, WebKit)
  - STATUS: done (owner-accepted exception) (2026-11-16). Run in order against a throw-away local PostgreSQL 18.6 on 127.0.0.1:5433 and the Vite dev server (not the Compose stack, because Docker and Podman are not installed): (1) backend `mvn clean verify`: BUILD SUCCESS, 188 unit and 233 integration/contract tests, 0 failures (the 233 include the 10 new `SecurityRegressionIT` tests); (2) frontend `npm ci` (0 vulnerabilities), `npm test` 362 of 362 and `npm run build` pass; (3) E2E-01..08 on all three browsers, 31 tests each: Chromium 31 of 31, WebKit 31 of 31 (3 workers), Firefox 31 of 31 (1 worker). Firefox with 3 workers had 2 failures, both 30 s timeouts under load (`E2E-04 a guest basket is merged at login...` and `E2E-05 a credit card payment shows the payment confirmation...`, the second inside `apiRequestContext.get`); the two tests passed when re-run alone and the whole Firefox project passed with 1 worker, and no code or test was changed. Not run: `docker compose --profile test up -d postgres-test` and the E2E run against the Compose frontend on `127.0.0.1:3000`. Compose steps skipped by owner exception.
- [X] T136 [P] Dependency scanning per PL-08: `mvn -f backend/pom.xml org.owasp:dependency-check-maven:check` (fail at CVSS ≥ 7.0; document any accepted lower finding), `npm audit --audit-level=high` in `frontend/` and `e2e/`; record results in `docs/security-scan-results.md`; confirm Mend access for the organisation's own scanning requirement — owner-accepted exception: scan not run
  - STATUS: done (owner-accepted exception) (2026-11-16). `npm audit --audit-level=high` in `frontend/` (154 packages) and `e2e/` (4 packages): 0 vulnerabilities. The backend OWASP Dependency-Check could not run: `NVD_API_KEY` is not set here, so the plugin stopped with `Invalid API Key` and `NoDataException` and no result exists. The Mend scan was not run (no access from this environment). Results and the open points are in `docs/security-scan-results.md`. Owner-accepted exception: scan not run.
- [X] T137 [P] Container and configuration review: images only from `registry.redhat.io`, non-root user, read-only root filesystem, dropped capabilities, host ports bound to `127.0.0.1`, no secrets in images or the repository (`git grep` for key patterns), TLS/`Secure` cookie settings documented for production in `README.md`
  - STATUS: done as a static review (2026-11-16); results in `docs/security-scan-results.md` section 6. Every checked item passes: Red Hat images only, `USER 1001`, read-only root file system, `cap_drop: [ALL]`, ports on `127.0.0.1`, no secret in the working tree, TLS and `Secure` cookie settings in `README.md`. Added `backend/.dockerignore` and `frontend/.dockerignore` (`.env`, `target/`, `node_modules/` stay out of build contexts). Docker and Podman are not installed here, so nothing was built or pulled; image signature and vulnerability scans, `docker compose config` and the `/dev/tcp` health checks are open points for T141. Observation for the owner: `backend` receives the whole `.env` including the Postgres variables.
- [X] T138 [P] Security test pass in `backend/src/test/java/com/bookygeek/integration/SecurityRegressionIT.java`: authorisation on every protected operation (customer A cannot read or change customer B's addresses, basket, orders, reward account), CSRF enforced on all mutating operations, no stack traces or SQL in error bodies, no sensitive data in logs
  - STATUS: done (2026-11-16). `SecurityRegressionIT` has 10 tests, all passing against the local PostgreSQL 18.6: customer isolation across addresses, basket, orders, buy-again and rewards (404, foreign `addressId` at placement 400, DB snapshot unchanged); all 19 cookie-protected operations without the cookie give 401 `UNAUTHENTICATED`; all 12 state-changing ones without, with a header-only, or with a mismatching CSRF token give 401 and change nothing; no class, SQL or table name in 10 kinds of error body (including a forced 500); SQL-injection text in search is inert; captured log output holds no password, card number, CVV, session token or JWT secret; stored hashes are salted `{pbkdf2}`. Limit: the log check covers the paths exercised here, not every possible log line. The forced 500 is an `ApiException(INTERNAL_ERROR)`, which the handler does not log, so the `Unexpected error` log line itself is not covered.
- [X] T139 [P] Non-functional checks recorded in `docs/nfr-verification.md`: performance targets (NFR-PERF), keyboard-only operation and contrast (NFR-ACC), responsive layout at 640 and 1024 px, supported browsers (NFR-BRW), wireframe comparison per `spec.md` §6.1 (SCR-01..SCR-xx) with screenshots
  - STATUS: done with recorded gaps (2026-11-16); evidence in `docs/nfr-verification.md`, collector in `docs/nfr-evidence/measure-nfr.cjs`, 56 screenshots (about 2.2 MB) in `docs/screenshots/`. Measured on loopback against a local PostgreSQL, the Spring Boot jar and the Vite dev server and production build, not the Compose stack (Docker is not installed): NFR-PERF-01 all 29 operations under 130 ms; NFR-PERF-02 worst case 288 ms. NFR-ACC-01: custom audit of 14 screens (0 unlabelled controls, 0 missing alt, 0 contrast failures, min 4.81:1) and a keyboard-only journey of 10 of 10 steps; no axe-core or screen-reader test, no skip link, not-found page has no h1. Responsive: 0 of 84 overflow. NFR-BRW-01: E2E 31 of 31 on Chromium, Edge 157, Firefox and WebKit; Chrome not installed. Two CSS defects found by the wireframe comparison were fixed (catalogue grid had 2 columns at 1280 px; book-detail covers stretched); frontend 362 of 362 and build pass after the fix. NOT met and left to the owner: illustrated background on SCR-08 and SCR-09, three-panel checkout, filter row not behind a toggle on mobile (RSP-03), two card columns at 1024 px. SC-003 is not claimed as met. Re-run the measurements on the Compose stack with T141.
- [X] T140 [P] Complete `README.md`: prerequisites (Java 21, Node, Docker/Podman, Red Hat registry login), `.env` set-up from `.env.example`, `docker compose up`, seeded demo data, test commands, gates and decisions summary, troubleshooting
  - STATUS: done (2026-11-16). README now has overview and stack, prerequisites, .env set-up, docker compose up --build, running the parts without Compose, seeded demo data and dummy payment values, test commands (including the postgres-test set-up), the configuration table, the production TLS/cookie section, the gate table, the rules in force and a troubleshooting table. Checked against the code before writing: password rule (8-64 characters, a letter and a digit), 60 seeded books (9 eBooks) and 19 leaf categories, JWT secret minimum, Node 22.12 engines, Playwright projects and E2E_BASE_URL. Not executed here: the Compose commands (Docker is not installed), so the Quick start steps 1-3 are unverified until T141.
- [X] T141 Run `quickstart.md` end to end on a clean checkout (`docker compose up --build`; browse to the frontend; walk E2E-01..08 manually once) and record any deviations
  - STATUS: done (owner-accepted exception) (2026-11-16). Docker and Podman are not installed on this machine, so `docker compose up --build` cannot be run. Open and unverified: `docker compose config`; image builds and pulls from `registry.redhat.io`; image signature and vulnerability scans; the `/dev/tcp` health checks; the README Quick start steps 1-3; the quickstart walk of E2E-01..08 on the Compose frontend (`127.0.0.1:3000`); the NFR measurements on the Compose stack (`docs/nfr-verification.md` section 9); and the T135 `postgres-test` and Compose E2E steps. Equivalent checks that were run without Compose (local PostgreSQL 18.6, Spring Boot jar, Vite) are in T135 and T139. Compose steps skipped by owner exception.
- [X] T142 Prepare the demo script `docs/demo-script.md` (journey order, test accounts created at registration, dummy payment values from PL-06)
  - STATUS: done (2026-11-16); `docs/demo-script.md` has the dummy values (account, address, cards, UPI, COD), the money rules with two worked examples (book 1: 563.95 and 281 points; book "Calm Mind, Clear Day": 406.45 and 203 points), a 15-scene journey and the known limits. The worked figures match the arithmetic and screenshots already verified; the Compose walk-through itself is not yet done (T141).
- [X] T143 Final consistency check: constitution v2.0.0 principles vs implementation (no Lombok, springdoc, Testcontainers, MapStruct, Actuator, Redux or TypeScript; no extra roles, wishlist, coupons, wallet), and `git status` clean of generated artifacts
  - STATUS: done (2026-11-16), by search of the working tree. Dependencies: no Lombok, springdoc, Testcontainers, MapStruct or Actuator in `backend/pom.xml`; no Redux or TypeScript in `frontend/package.json` or `e2e/package.json`; no `.ts` or `.tsx` file under `frontend/src`. Excluded features: no wishlist, coupon, wallet, Bestsellers, New Launches, My Writers or Book Worm in `backend/src/main` (the only hit is a comment in `V1__init_schema.sql` saying they are not modelled) or in non-test `frontend/src`; they appear only in the banned-strings test that proves they are never rendered. Roles: the only authority in the backend is `ROLE_CUSTOMER` (`JwtAuthenticationFilter`); no admin or other role. Generated artifacts: `.gitignore` covers `.env`, `node_modules/`, `target/`, `dist/`, `test-results/`, `playwright-report/` and `coverage/`; `git check-ignore` confirms `frontend/node_modules`, `backend/target`, `frontend/dist` and `.env`; no untracked `.env`, `node_modules`, `target`, `dist`, `test-results` or `playwright-report` file is listed by `git ls-files --others --exclude-standard`; `e2e/test-results` and `e2e/playwright-report` were deleted after the last run. `docs/` holds no secret pattern; the evidence script generates throw-away passwords at run time. Notes: `git status` is not empty because the work is uncommitted (commit needs approval), and `specs/001-bookygeek-platform/spec.md` and `.specify/memory/constitution.md` show as modified from before this work. `docs/screenshots/` (56 JPEGs, about 2.2 MB) is not ignored and would be committed; the owner may prefer to drop it.

---

## Phase 11: Capstone submission and verification

**Purpose**: Deliverables required by the Cloud Fullstack Capstone deck (`Application Developer - Cloud FullStack - Capstone instructions.pptx`, slides 11–13) that Phases 1–10 do not already cover: manual API testing, frontend code review, complete local UI verification, and the cloud deployment status. Git, GitHub, Pull Request, evaluator access, submission package and demo video are manual owner activities and not tracked tasks.
**Scope guard**: Verification, review, packaging and documentation only. No new endpoints, entities, screens or features; the modular monolith, `api/openapi.yaml`, `data-model.md` and `spec.md` are unchanged. Defects found here are fixed in the owning module with its tests re-run; any contract change returns to the owner (G-API). Wishlist, wallet, coupons, reviews, author profiles and real payment integration remain excluded.
**Overlap with existing tasks (not duplicated)**: T135 (automated test suites), T139 (NFR, responsive and wireframe evidence) and T141 (clean-checkout `quickstart.md` run) are reused as evidence; the tasks below start from their results and do not repeat them.

**Capstone cross-reference**

| Capstone requirement | Slide | Task |
|---|---|---|
| Test APIs (GET, POST, PUT, DELETE) with Insomnia or equivalent; responses match the API specification; data saved in the database | 12 (Step 8) | T145 |
| Review generated code: component structure, folder organisation, reusable components, naming, duplicate or unnecessary code | 13 (Step 3) | T146 |
| Run and test all screens locally: buttons, forms, navigation, cart/payment flows; responsiveness; refine | 13 (Steps 5–6), 11 | T147 |

- [X] T145 [P] **Manual API testing** with Postman, Insomnia or an equivalent tool, using only the operations in the approved `api/openapi.yaml` (import the file; do not add requests for paths or methods that are not in it). Run against the local Compose stack with the seeded data. Record the request set (exported collection without credentials, cookies or tokens) in `docs/api-manual-tests/` and the results in `docs/api-manual-test-report.md`. Cover every applicable operation by method as defined in the contract: GET (`getCurrentCustomer`, `listAddresses`, `listStates`, `listCategories`, `listBrands`, `listBooks`, `getBookFilterOptions`, `getBook`, `listRelatedBooks`, `getBasket`, `getBasketRecommendations`, `getRewardAccount`, `listOrders`, `getOrder`), POST (`register`, `login`, `logout`, `createAddress`, `previewBasket`, `addBasketItem`, `mergeGuestBasket`, `quoteCheckout`, `placeOrder` with an `Idempotency-Key`, `cancelOrder`, `buyAgain`), PUT (`updateAddress`, `updateBasketItem`) and DELETE (`deleteAddress`, `removeBasketItem`). For each operation record at least one successful response, the validation errors defined for it (`VALIDATION_FAILED` with `fieldErrors`, `QUANTITY_LIMIT`, `OUT_OF_STOCK`, `POINTS_LIMIT_EXCEEDED`, `PAYMENT_DECLINED`, `CANCELLATION_WINDOW_EXPIRED`, etc., using the PL-06 dummy payment values), and the authentication and authorisation behaviour: protected operations without the `BG_AUTH` cookie return 401 `UNAUTHENTICATED` as an `ApiError`, mutating calls without `X-XSRF-TOKEN` are rejected, a replayed cookie after `logout` is rejected, and another customer's address, basket item, order or reward data is answered as the contract specifies (not found). Confirm that status codes, bodies and `ApiError` shape match the contract, and that persisted effects (customer, address, basket, order, stock, reward balance) are visible in the local PostgreSQL by read-only queries. Any mismatch is a defect: fix the implementation (never loosen the contract) and re-run the relevant automated tests. (depends on T022, T132, T133, T135, T138)
  - STATUS: done (2026-11-17). `docs/api-manual-tests/api-manual-test.cjs` covers all 29 operations, 60 cases, 60 passed, no implementation defect; report in `docs/api-manual-test-report.md`. Run on the local stack, not Compose.
  - **Acceptance**: every one of the 29 contract operations has at least one executed request; GET, POST, PUT and DELETE cases all pass or have a linked, fixed defect; each documented error path and 401/CSRF/ownership case is recorded with expected versus actual result; no request targets an endpoint absent from `api/openapi.yaml`; the exported collection and report contain no real secrets, tokens or cookie values; `docs/api-manual-test-report.md` is complete.
- [X] T146 [P] **Frontend code review** of the implemented `frontend/src/` and a recorded outcome in `docs/frontend-code-review.md`. Check against plan.md "Frontend design" and the frontend conventions: component structure (one component per file with a same-named CSS file, function components and hooks), folder organisation (`api/`, `components/common|layout|book|checkout`, `context/`, `hooks/`, `pages/`, `styles/`, `utils/`, `test/`), naming conventions (PascalCase components and files, camelCase functions and variables), reusable components used instead of copies (`Button`, `Field`, `Modal`, `Spinner`, `ErrorMessage`, `EmptyState`, `BookCard`), duplicate or unnecessary code (repeated markup, validation, formatting, CSS blocks, unused files, exports, dependencies and commented-out code), and consistency with the approved architecture: Axios created only in `api/axiosClient.js`, `AuthContext` and `BasketContext` as the only global state, shared `validators.js` and `money.js`/`date.js` used everywhere, `useAsync` loading/error/empty states on every data view, one dark theme from `tokens.css` with no toggle, no TypeScript, Redux, `dangerouslySetInnerHTML`, secrets, card data in storage, or banned UI strings. Refactor findings that are behaviour-neutral and within the approved scope, then re-run `npm test` and `npm run build`. (depends on T124, T131, T132)
  - STATUS: done (2026-11-17). `docs/frontend-code-review.md`; 4 unused exports made private; `npm test` 362 of 362 and `npm run build` pass.
  - **Acceptance**: `docs/frontend-code-review.md` lists the checklist items checked, findings and their resolution (fixed or consciously accepted with a reason); no duplicate components or copied validation/formatting logic remain unresolved; naming and folder layout match plan.md; `npm test` and `npm run build` pass after the refactor; the review adds no new feature, route or dependency.
- [X] T147 **Complete local UI verification** of every in-scope screen, performed manually in a browser against the running local stack (`docker compose up`, seeded data), and recorded in `docs/ui-verification.md` as a screen-by-screen checklist (SCR-01..SCR-13 from `spec.md` §4; SCR-14 excluded features confirmed absent). Walk: header and sidebar navigation, breadcrumbs and CL-25 navigation (category tree, breadcrumb levels across the category hierarchy, genre-tag links, and author-name links that open the catalogue filtered by that author); register, login, logout and `returnTo`; catalogue with category tree, brands, search, language/format/price filters, sort and paging; book details, related books and Add to Basket; basket (guest, merge at login, steppers, remove, totals, recommendations); checkout, saved addresses and reward redemption; payment (credit card, debit card, UPI and cash on delivery, both approved and declined values, retry after decline); payment confirmation and purchase confirmation; My Orders, order detail, cancel within 48 hours and Buy Again; empty, loading, error and not-found states; form validation messages. Verify at desktop width and at the approved tablet and mobile widths (640 px and 1024 px breakpoints) that nothing overflows horizontally and every action stays reachable. Compare the supplied screens (SCR-04, 05, 07, 08, 09) with the five PNGs in `Capstone_frontend_images/`, reusing the T139 screenshots and checking any screen T139 did not cover. Confirm no broken link, dead end or non-functional control exists within the approved scope, and that no excluded element is shown (wishlist, My Writers, reviews, ratings, coupons, wallet, "Bestsellers this Month", "New Launches", "Book Worm"). Fix defects in the owning component, add or adjust its test, and re-verify. (depends on T139, T146)
  - STATUS: done (2026-11-17). `docs/ui-verification.md` built from T139 and E2E evidence; no defect found; SC-003 deviations accepted (see exceptions below).
  - **Acceptance**: `docs/ui-verification.md` shows every in-scope screen and journey as passed, with browser and viewport used and a screenshot reference for the wireframe-based screens; the CL-25 navigation is verified as follows: (1) category tree navigation lists only the selected category's books (a parent includes its sub-categories) and highlights the selection; (2) breadcrumb links across category levels open the catalogue at that level; (3) genre-tag links filter the catalogue by that genre; (4) clicking an author name opens the catalogue filtered by that author, as specified in CL-25; (5) author links and author-based filtering work correctly on every screen that shows an author name (catalogue book cards, Recommended for You, book details and Related Reads); zero broken links and zero inactive buttons or links in the approved scope; the purchase journey completes for all four payment methods and a declined payment changes nothing; deviations from the wireframes are only the approved ones recorded in `spec.md` (CL-04, CL-05, CL-06, CL-09, CL-15, CL-17); each defect found is fixed and re-verified or explicitly accepted by the owner.
- [X] T153 **Record the cloud deployment status**, whichever applies: Cloud deployment is not performed (owner-accepted exception). Add a "Cloud deployment status" section to `docs/deployment.md` stating that deployment was not performed and why, and pointing to the working local setup instructions in `README.md` and `quickstart.md` (`docker compose up`, `.env` from `.env.example`, seeded data, demo accounts created by registering, dummy payment values).
  - STATUS: done (2026-11-17). `docs/deployment.md` written: not deployed; run locally per README.md.
  - **Acceptance**: `docs/deployment.md` states exactly one of "deployed and verified" or "not deployed, with reason and local instructions"; the local setup instructions in the document match the verified `README.md`; cloud deployment is not performed.

---

## Phase 12: Amendment AMD-002 (owner-approved 2026-11-18)

**Purpose**: Implements `amendments/AMD-002-reviews-profile-cart.md`. G-DM and G-API changes for the `review` table and the two review operations are approved by the owner (contract version 1.1.0, 31 operations).

- [x] T156 [US4] **Cart quantity control**: shared `QuantityControl` (`frontend/src/components/basket/QuantityControl.jsx`) used on `BookCard`, `BookCardCompact` and `BookDetailPage` with the existing `BasketContext`/basket API only. **Done when**: "Add to Cart" shows when the book is not in the basket; when it is, minus ("Decrease quantity"), the quantity, plus ("Increase quantity") and a "Go to Cart" link to `/basket` show; minus at 1 removes the line; plus at `min(10, stock)` is disabled with "Maximum quantity reached"; an unavailable book shows no control; works for guests and customers; `QuantityControl.test.jsx` passes.
- [x] T157 [US1] **Profile page**: `/profile` (protected, `returnTo`), header name links to it, "My Profile" with "Personal Details", "Gift Points" (`<balance> pts`), "Saved Addresses" (existing add/edit/delete reused) and a "Log out" button; loading, error with Retry and empty states; no wallet, Edit Name or history. **Done when**: the page renders those cards from `getCurrentCustomer`, `getRewardAccount` and `listAddresses`, a guest is redirected to login with `returnTo`, and its Vitest tests and the Playwright scenario pass.
- [x] T158 [US3] **Reviews and description**: Flyway `V2__review.sql`, `listBookReviews` and `createBookReview` (Catalogue module), `ALREADY_REVIEWED` error code, description shown on the detail page, average stars near the price, "Reviews" section with the form and list. **Done when**: `ReviewIT` passes (empty list with null average, create then list, HALF_UP average, 409 `ALREADY_REVIEWED`, 400 for rating 0 and 6, 401 unauthenticated, 404 unknown book), the module-boundary test passes, the OpenAPI contract tests count 31 operations, and the Playwright scenario submits a review and sees it listed.
- [x] T159 [US3] **Author section**: "About the writer" below the book details and above Reviews: author name and the author's other books via `listBooks` `author`, excluding the current book; only the name when there are none. **Done when**: the section renders as described in a Vitest test and is visible in the Playwright scenario.
- [x] T160 **Mandatory field asterisks**: `required` prop on `Field` (visible ` *` in `aria-hidden` span and native `required`), applied to registration, login, address form (not line 2), card form, UPI form and the review rating, with one "* required" line per form. **Done when**: Vitest tests for `Field` and the forms pass, and existing client and server validation is unchanged.
- Wishlist and Follow writer are frontend-only (localStorage), owner-approved.


## Phase 13: Amendment AMD-003 (owner request 2026-11-18)

**Purpose**: Implements `amendments/AMD-003-writers-seed-data-covers.md` (contract version 1.2.0, still 31 operations).

- [x] T161 [US3] **Writer biography**: `V3__author_profile.sql`, `AuthorProfile` entity and repository, `authorBio` on `BookDetail`, shown in "About the writer". **Done when**: `BookDetailServiceTest` and a Vitest test cover the bio, and `getBook` returns `authorBio` (null when the writer has no profile).
- [x] T162 **Seed data**: 12 writers with five books each and a 50-word bio each; five seeded reviews per book from ten reader customers (`scripts/generate-seed-data.mjs`). **Done when**: `CatalogueQueryIT` proves five books and a 50-word bio per writer, and `/books/{id}/reviews` returns reviews for any book.
- [x] T163 **Covers**: 60 front and 60 back covers generated from each book's title, author and description (`frontend/scripts/generate-covers.mjs`). **Done when**: the SVGs exist for ids 1..60 and a visual check shows covers that match the titles.
- [x] T164 **Layout**: "Related Reads" no longer stretches the first row of the detail page, so there is no gap above "About the writer" and "Reviews". **Done when**: a screenshot of `/books/1` shows the sections directly below the details.
- [x] T165 **E-book checkout without address** (AMD-004): for a basket of e-books only, checkout shows no address fields, `placeOrder` accepts no address and stores none (`V4__ebook_order_without_address.sql`), and the confirmation and order pages omit "Delivery address"; a basket with a physical book still requires one. **Done when**: `OrderServiceTest`, `PlaceOrderIT`, the Vitest checkout and payment tests and E2E-05 pass.





---

## Dependencies and execution order

### Phase dependencies

- **Phase 1 (Setup)**: no dependencies. T014 depends on T003–T011.
- **Phase 2a (G-API)**: depends on Phase 1 only for repository layout (T001). Sequential: T015 → T016 → T017 → T018 → T019 → T020 → T021 → T022 → T023. **T022 (owner approval) is the single gate for all backend implementation.**
- **Phase 2b (frontend foundation)**: depends on T006, T007. Can run in parallel with 2a and in parallel with 2c.
- **Phase 2c (backend foundation)**: **blocked by T022**. Internal order: T035/T036 → T037 → T038 → T039 → T040 → T041/T042 → T043 → T044/T045 → T046.
- **Phases 3–9 (user stories)**: backend tasks blocked by T022 and by T046; frontend tasks need Phase 2b and use fixtures until the matching backend operations pass their contract tests.
- **Phase 10 (Polish)**: depends on every story intended for release.
- **Phase 11 (Capstone verification)**: depends on Phase 10 evidence (T133–T143). Order: T145 ∥ T146 → T147 → T153. T145 is additionally blocked by T022 (G-API approval).

### User-story dependencies

| Story | Depends on | Notes |
|---|---|---|
| US1 | Phase 2 | Includes the full `RewardService` (T055) because registration creates the reward account and Order uses it later. |
| US2 | Phase 2 | Independent of US1 for guests. |
| US3 | US2 (`CatalogueService`, `BookController`, `catalogueApi.js`) | |
| US4 | US1 (merge at login), US2/US3 (Add to Basket) | `PricingService` (T090) is reused by US5 and US7. |
| US5 | US1, US4 | Backend `placeOrder` already handles `pointsToRedeem` (via `RewardService`), so US7 adds only the endpoint, UI and boundary tests. |
| US6 | US5 (orders must exist), US4 (`CartService`) | Delivers FR-BSK-07 and the Recommended for You sections left open in US2/US4. |
| US7 | US5 (checkout page and `placeOrder`), US1 (`RewardService`) | |

### Within each user story

1. Test tasks first (they fail first).
2. Entities/repositories → services → controllers → frontend API module → pages.
3. The final "Run and fix" task closes the story; the story is done only when its tests, including its Playwright scenario, pass.

### Parallel opportunities

- Phase 1: T003–T010, T012, T013 together after T001.
- Phase 2: T024–T027, T029, T030, T033, T034 (frontend) in parallel with T015–T023 (contract authoring); T036, T041, T042, T044, T045 in parallel inside 2c once their predecessors finish.
- Per story, all `[P]` test tasks run together, then `[P]` entities and frontend modules together.
- Once Phase 2 is complete, different developers can take US1, US2 and then US3 in parallel; US4 starts when US1 and US3 are available.
- Phase 11: T145 and T146 together; T147 follows, then T153.

### Parallel example: User Story 2

```text
Tests together:      T062  T063  T064  T065  T066  T067
Entities together:   T068 (Category module)   T069 (Book entity)   T072 (catalogueApi)   T073 (BookCard)
Then sequential:     T070 → T071 → T074 → T075
```

### Parallel example: User Story 5

```text
Tests together:      T097  T098  T099  T100  T101  T102  T103  T104
Entities together:   T105 (Address)   T106 (Payment)   T107 (Order)   T111 (frontend APIs)   T113 (confirmation pages)
Then sequential:     T108 → T109 → T110 → T112 → T114
```

---

## Implementation strategy

### MVP first (US1 → US5)

1. Complete Phase 1 and Phase 2 (including the G-API owner approval, T022).
2. Deliver US1 (accounts) → US2 (catalogue) → US3 (detail) → US4 (basket) → US5 (checkout and dummy payment). This delivers every P1 story and the end-to-end purchase journey.
3. **Stop and validate** E2E-01..06 against the quickstart before continuing.
4. Add US6 (orders, cancellation, Buy Again, recommendations), then US7 (reward redemption UI), then Polish, then Phase 11 (capstone verification).

### Incremental delivery

- Each story ends with a Run-and-fix task and is demonstrable on its own using the seed data and the stories before it.
- Do not start backend work for a story before T022 and T046. Frontend work may start earlier with fixtures but must switch to the live API before the story is closed.

### Rules carried through every task

- Follow `api/openapi.yaml` exactly; any contract change returns to the owner (G-API) and updates the contract test.
- Services are concrete classes; modules talk to each other only through services, never repositories or entities; `ddl-auto=validate` always.
- Money is `NUMERIC(12,2)` / `BigDecimal` / decimal string everywhere; no floating-point arithmetic.
- No secrets, card data or personal data in logs; all host ports on `127.0.0.1`; containers non-root.

## Notes

- `[P]` tasks touch different files and have no incomplete dependencies.
- Commit after each task or logical group.
- Re-verify the Spring Boot 4.x pin (T003) and the exact UBI image tags (T009, T010) at scaffolding time; they are environment facts, not planning decisions.

## Owner-accepted exceptions (NFR-TST-05)

1. Docker, Compose and the Red Hat registry are skipped; verification on local PostgreSQL 18.6 (127.0.0.1), the Spring Boot jar and the Vite dev server is accepted (T014, T046, T135, T141).
2. Git, GitHub, Pull Request, evaluator access, submission package and demo video are manual owner activities, not tracked tasks.
3. Cloud deployment is not performed (T153).
4. OWASP Dependency-Check and Mend scans are not run (no NVD key or Mend access); the npm audit result (0 vulnerabilities) stands (T136).
5. SC-003 gaps recorded in T139 (SCR-08/09 illustrated background, three-panel checkout, mobile filter toggle, two card columns at 1024 px) are accepted deviations (T147).
- AMD-002: `reviewerName` is read through `AuthService.getCurrentCustomer`; `AddToBasketButton` was replaced by `QuantityControl` (label "Add to Cart"), so existing tests and E2E-03..08 that used "Add to Basket" or "N in your basket" were updated; after AMD-003 every writer has five books, so E2E-09 checks the biography and the four other books; `ReviewIT` has no `openapi.yaml` schema check (the contract helper is package-private), but the OpenAPI file lists 31 operationIds.
