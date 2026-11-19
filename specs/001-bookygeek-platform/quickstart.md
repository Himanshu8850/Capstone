# Quickstart & Validation Guide: BookyGeek

**Feature**: `001-bookygeek-platform` | **Plan**: [plan.md](plan.md) | **Contract**: [contracts/api-contract-outline.md](contracts/api-contract-outline.md) | **Data**: [data-model.md](data-model.md)

> Validation/run guide only. Commands are the **target** commands once the corresponding implementation phase
> is done; nothing here exists yet. No secrets are committed: copy `.env.example` to `.env` (git-ignored) and
> fill in values.

## 1. Prerequisites

- JDK 21, Maven 3.9+, Node.js (current LTS), a local PostgreSQL 18 instance running on 127.0.0.1.
- Browsers: latest Chrome, Edge, Firefox.

## 2. Configure (environment variables — names only, values are yours)

| Variable | Purpose |
|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | database creation and credentials |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | backend DB connection |
| `JWT_SECRET` | ≥ 32 random bytes (generate with `openssl rand -base64 48`) |
| `JWT_TTL_MINUTES` | default 60 |
| `COOKIE_SECURE` | `false` only for plain-HTTP localhost |
| `CORS_ALLOWED_ORIGINS` | empty by default (same-origin) |
| `SPRING_PROFILES_ACTIVE` | `dev` (loads seed), `test`, `prod` |
| `BOOKYGEEK_REWARDS_INITIAL_POINTS` | default 1000 |

## 3. Start everything (target)

Backend: `cd backend && mvn spring-boot:run` (binds `127.0.0.1:8080`).
Frontend: `cd frontend && npm ci && npm run dev` (Vite on `127.0.0.1:5173`, proxy `/api` → backend).

Open `http://127.0.0.1:5173`.

## 4. Smoke checks

1. `GET http://127.0.0.1:8080/api/v1/categories` → 200 with the two-level tree (19 leaves).
2. Landing page shows 12 book cards and a paginator; seeded database has ≈ 60 listed books.
3. Flyway log shows all migrations applied; Hibernate `validate` passes (no auto-DDL).

## 5. Dummy test values (documented per FR-PAY-08)

| Case | Input | Result |
|---|---|---|
| Card success | `4111-1111-1111-1111`, name `Test Reader`, CVV `123`, expiry in the future `MM/YYYY` | Paid |
| Card declined | `4000-0000-0000-0002` | `PAYMENT_DECLINED`; no order, stock/points/basket unchanged |
| UPI success | `demo@upi` | Paid |
| UPI declined | `failure@upi` | `PAYMENT_DECLINED` |
| COD | no details | order Placed, payment Pending (pay on delivery) |
| Address | phone `9876543210`, PIN `560001`, state `Karnataka`, country India | valid |
| Invalid | phone 9 or 11 digits; PIN 5 or 7 digits | blocked inline + server 400 |
| New account | e.g. `reader@example.test` / `Passw0rdOK` | starts with 1,000 points |

## 6. Validation scenarios (end-to-end; each is a Playwright test)

| # | Journey (spec §6.1) | Steps → expected |
|---|---|---|
| E2E-01 | 1–2 Register/login/logout | Register → login → account menu shows name → logout → `/orders` redirects to login → login returns to `/orders` |
| E2E-02 | 3, 5, 6 Browse | Guest opens `/`, picks Non-Fiction → Self Help, picks a brand, searches, filters Language+Format+Price, sorts, pages (12/page) |
| E2E-03 | 7 Details | Open a book: details, delivery date (physical today+5, eBook today), related books same category, breadcrumb |
| E2E-04 | 8 Basket | Guest adds two books → login → merged basket (PL-01 sums, capped) → change qty, remove, 11th unit rejected, badge correct |
| E2E-05 | 9–12 Checkout per method | For each of CC, DC, UPI, COD: address (new and saved) → redeem points → pay → payment confirmation (not for COD) → purchase confirmation; basket empty; order in My Orders |
| E2E-06 | 10–11 Failure | Declined card/UPI → message; no order; points, stock, basket unchanged; retry succeeds |
| E2E-07 | 4, 12 Orders | My Orders; Buy Again (unavailable book listed, over-stock reduced); cancel an order (<48h): status, stock, points, payment updated |
| E2E-08 | Rewards | Max redeemable = min(balance, floor(50% of eligible total)); one more point rejected |
| — | Responsive/browser/a11y | Walk each journey at <640, 640–1023, ≥1024 px; Chrome/Edge/Firefox; keyboard-only; labels/alt/contrast |

Boundary and concurrency checks (backend integration, fixed `Clock`): cancellation at 47:59:59 accepted and
48:00:00 rejected (also for COD); two threads buying the last unit → exactly one order; double submit with the
same `Idempotency-Key` → one order; mid-operation failure rolls everything back.

## 7. Run the test suites (target)

```text
# Ensure local test PostgreSQL is running (e.g. on 127.0.0.1:5433)
cd backend  && mvn clean verify                 # JUnit 5 + Mockito + Spring Boot Test; needs test DB
cd frontend && npm ci && npm test && npm run build  # Vitest + RTL, production build
cd e2e      && npm ci && npx playwright test        # needs backend and frontend running
```

Dependency scans: `mvn org.owasp:dependency-check-maven:check` and `npm audit --audit-level=high`; attach the reports.

## 8. Optional cloud deployment (never required)

Only if a free-tier container host and a managed/free PostgreSQL are available: deploy the same two images,
provide the Section 2 variables via the host's secret store, set `COOKIE_SECURE=true`, terminate TLS 1.2+ with
a valid certificate, and run Flyway migrations at start-up (schema only; seed profile off in `prod`). The
application is fully demonstrable without this step.
