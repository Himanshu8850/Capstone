# BookyGeek

Online bookstore: a React single-page app, a Spring Boot 4.x modular-monolith API and PostgreSQL, run locally. Capstone project for the Cloud Fullstack programme.

## Overview

A customer can register and log in, browse and search the catalogue (categories, brands, filters, sort, 12 books per
page), open a book, fill a basket (as a guest too; it is merged at login), check out with a saved or new address,
redeem reward points, pay by credit card, debit card, UPI or cash on delivery (all simulated), see the confirmation, view
My Orders, Buy Again and cancel an order within 48 hours.

| Part | Technology |
|---|---|
| Frontend | React with Vite, React Router, Axios, CSS (one dark theme), Vitest and React Testing Library |
| Backend | Java 21, Spring Boot 4.1.2, Spring Security (JWT in an HttpOnly cookie, CSRF), Spring Data JPA, Flyway, Bean Validation |
| Database | PostgreSQL 18 |
| Tests | JUnit 5, Mockito, Spring Boot Test (backend); Vitest (frontend); Playwright (end to end) |

The backend is one deployable made of seven business modules (`user`, `reward`, `category`, `catalogue`, `cart`,
`payment`, `order`) plus `common`. An architecture test (`ModuleBoundaryTest`) stops a module from using another module's
repository or entity.

Out of scope, by decision: wishlist, wallet, coupons, reviews, author profiles, a real payment gateway, and the
"Bestsellers" and "New Launches" sections. Payment is simulated; no card data is stored or logged.

Where things are:

| Path | Content |
|---|---|
| `api/openapi.yaml` | The approved API contract (29 operations); `api/TRACEABILITY.md` maps each one to code and tests |
| `backend/`, `frontend/`, `e2e/` | The three code bases |
| `specs/001-bookygeek-platform/` | Specification, plan, data model, tasks, quickstart |
| `docs/` | Test matrix and security scan results; later NFR evidence and the demo script |
| `.env.example` | Configuration template |

## Prerequisites

| Need | For | Note |
|---|---|---|
| JDK 21 and Maven 3.9 | Running and testing the backend | The POM targets Java 21 |
| Node.js 22.12 or newer and npm | Frontend and end-to-end tests | `engines` in both `package.json` files |
| PostgreSQL 18 | Local database | Local PostgreSQL running on 127.0.0.1 |
| Chromium, Firefox, WebKit for Playwright | End-to-end tests | `cd e2e && npm run install:browsers` |

## Quick start

1. Create your local configuration. `.env` is git-ignored; never commit it.

   ```text
   cp .env.example .env          # PowerShell: Copy-Item .env.example .env
   ```

   Edit `.env` and replace every `CHANGE_ME`:
   - `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`: your local PostgreSQL credentials.
   - `JWT_SECRET`: a random value of at least 32 bytes, for example `openssl rand -base64 48`. The backend refuses to start
     with a missing or short secret.
   - Keep `COOKIE_SECURE=false` and `SPRING_PROFILES_ACTIVE=dev` for plain-HTTP localhost.

2. Start the backend:

   ```text
   cd backend
   mvn spring-boot:run
   ```

   The backend API runs on `http://127.0.0.1:8080/api/v1`.

3. Start the frontend:

   ```text
   cd frontend
   npm ci
   npm run dev
   ```

   The frontend runs on `http://127.0.0.1:5173` (with `/api` proxied to `http://127.0.0.1:8080`).

4. Open `http://127.0.0.1:5173`. A first smoke check: `GET http://127.0.0.1:8080/api/v1/categories` returns the category
   tree, and the landing page shows 12 book cards with a paginator.

### Seeded demo data

With `SPRING_PROFILES_ACTIVE=dev` (and `test`), Flyway also loads a reproducible seed: the two-level category tree
(19 leaf categories) and 60 books (paperback, hardcover and 9 eBooks) with prices and stock. The "brand" of a book is its
publisher; there is no separate brand table.
The seed never overwrites existing rows. With `prod` only the schema is migrated, so a production database starts with no
books.

There are no pre-made customer accounts. Register one in the app (for example `reader@example.test`, a name and a
password of at least 8 characters with letters and digits); every new account starts with 1,000 reward points.

Dummy values for the simulated payment:

| Case | Input | Result |
|---|---|---|
| Card approved | `4111-1111-1111-1111`, any name, CVV `123`, an expiry in the future (`MM/YYYY`) | Paid |
| Card declined | `4000-0000-0000-0002` | Declined; no order is created |
| UPI approved | `demo@upi` | Paid |
| UPI declined | `failure@upi` | Declined; no order is created |
| Cash on delivery | no details | Order placed, payment pending on delivery |

## Test commands

| Suite | Command | Needs |
|---|---|---|
| Backend unit and integration | `cd backend && mvn clean verify` | A PostgreSQL test database (below) |
| Frontend | `cd frontend && npm ci && npm test && npm run build` | Nothing else |
| End to end | `cd e2e && npm ci && npx playwright test` | The full stack running (frontend on `E2E_BASE_URL`, default `http://127.0.0.1:5173`) |
| Dependency audit (npm) | `npm audit --audit-level=high` in `frontend/` and `e2e/` | Network |
| Dependency check (Java) | `mvn -f backend/pom.xml org.owasp:dependency-check-maven:check` | An NVD API key in `NVD_API_KEY` |

Integration tests (`*IT.java` and `contract/*Test.java`, run by Failsafe) need a local PostgreSQL database (e.g. on `127.0.0.1:5433` or `127.0.0.1:5434`):

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/bookygeek_test
SPRING_DATASOURCE_USERNAME=<your db username>
SPRING_DATASOURCE_PASSWORD=<your db password>
```

The Playwright projects are `chromium`, `firefox` and `webkit`; select one with `--project=chromium`. `E2E_BASE_URL` defaults to `http://127.0.0.1:5173`.

What the tests cover, requirement by requirement, is in [`docs/test-matrix.md`](docs/test-matrix.md).

## Configuration

All values come from `.env` (names and placeholders in `.env.example`).

| Variable | Purpose |
|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Database credentials |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Backend database connection |
| `JWT_SECRET` | Signing secret, at least 32 random bytes |
| `JWT_TTL_MINUTES` | Login lifetime, default 60 |
| `COOKIE_SECURE` | `false` only for plain-HTTP localhost |
| `CORS_ALLOWED_ORIGINS` | Empty by default (or allowed origins if frontend is separate) |
| `SPRING_PROFILES_ACTIVE` | `dev` (schema and seed), `test`, `prod` (schema only) |
| `BOOKYGEEK_REWARDS_INITIAL_POINTS` | Points for a new account, default 1000 |

### Production: TLS and cookies

The application itself speaks plain HTTP. In any shared or production environment, put a TLS terminator in front
(TLS 1.2 or higher, TLS 1.3 preferred; a valid, trusted certificate; never disable certificate validation), and keep these settings:

| Setting | Value | Why |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Runs schema migrations only (no demo seed data) and sets the cookie `Secure` flag to `true`. |
| `COOKIE_SECURE` | `true` | The `BG_AUTH` and `XSRF-TOKEN` cookies are sent over HTTPS only. `false` is allowed only for plain-HTTP localhost. |
| `JWT_SECRET` | A random value of at least 32 bytes (for example `openssl rand -base64 48`), from a secret store | Never commit it; rotate it when it may have leaked. |
| `CORS_ALLOWED_ORIGINS` | Allowed origins | Add an origin when the frontend is served from another origin. |

The login cookie `BG_AUTH` is `HttpOnly`, `SameSite=Strict` and scoped to `/api`. The CSRF cookie `XSRF-TOKEN` is
deliberately readable by the page script (the single-page app copies it into the `X-XSRF-TOKEN` header), is
`SameSite=Strict` and is scoped to `/`. Both get the `Secure` flag from `COOKIE_SECURE`. All local services bind to
`127.0.0.1`; do not change them to `0.0.0.0`. Never commit `.env`; copy `.env.example` and replace every `CHANGE_ME` value locally.

## Gates

The project follows Spec Kit gates. Approved so far (details in `specs/001-bookygeek-platform/plan.md`, Gate Register):

| Gate | What it covers | Status |
|---|---|---|
| G-PLAN | The implementation plan and its additions register | Approved 2026-11-12 |
| G-AMD-001 | Spring Boot 3.x to 4.x (constitution 2.0.0) | Approved 2026-11-12 |
| G-DM | `data-model.md` (11 tables) | Approved 2026-11-12 |
| G-API | `api/openapi.yaml` | Approved 2026-11-14 |

Changing the API contract, the data model, the plan or the constitution needs the owner's written approval.

## Decisions and rules in force

| Area | Rule |
|---|---|
| Architecture | Modular monolith with seven business modules plus `common`; no microservices |
| Security | Login by JWT in an `HttpOnly` cookie; CSRF header on every state-changing request; passwords as salted PBKDF2 hashes; another customer's data is answered as 404; every security failure is the contract's 401 `UNAUTHENTICATED` |
| Money | Tax 5% of the subtotal (half up); delivery 40 for physical and mixed orders, 0 for eBook-only |
| Rewards | 1 point = 1 rupee; at most `min(balance, floor(50% of subtotal + tax + delivery))` per order; points are never earned |
| Basket | 1 to 10 copies of a book per line, and never more than the stock of a physical book |
| Cancellation | Only a placed order, only strictly under 48 hours after placing (cash on delivery too); stock and points are restored; a paid order becomes refunded, cash on delivery becomes cancelled |
| Payments | Simulated; card and UPI details are validated and never stored or logged; an `Idempotency-Key` makes a retry safe |
| Paging | 12 books per page |

## Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| Backend exits at start-up with a message about the JWT secret | `JWT_SECRET` is missing, shorter than 32 bytes or still `CHANGE_ME`; generate a new one |
| Backend cannot connect to the database | Verify PostgreSQL is running on 127.0.0.1 and credentials in `SPRING_DATASOURCE_*` match |
| Login seems to work but every request is 401 | `COOKIE_SECURE=true` on plain HTTP: the browser drops the cookies. Use `false` on localhost only |
| `bind: address already in use` | Another program uses 5173, 5432, 5433 or 8080 on 127.0.0.1; stop it or configure a different port |
| The catalogue is empty | The profile is `prod` (schema only) or the seed did not run; use `SPRING_PROFILES_ACTIVE=dev` for demo data |
| Integration tests fail at start with a connection error | The test database is not running or the `SPRING_DATASOURCE_*` variables are not set in the same shell |
| Playwright says a browser is missing | `cd e2e && npm run install:browsers`, or pass `--project=chromium` |
| End-to-end tests cannot reach the app | Start the stack first and, if you use Vite, set `E2E_BASE_URL=http://127.0.0.1:5173` |
| Dependency-Check stops with `Invalid API Key` | Request a free NVD API key and put it in `NVD_API_KEY` for that command only |
