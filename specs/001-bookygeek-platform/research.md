# Phase 0 Research & Decisions: BookyGeek

**Feature**: `001-bookygeek-platform` | **Date**: 2026-11-12 | **Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md)

All Clarifications recorded in the spec are final and are **not** reopened here. This document only
resolves the plan-level items **PL-01..PL-10** (spec §10.2) and the technical choices needed to plan the
build. No item below changes scope, architecture, the seven modules or a business rule. Technical Context
contains **no remaining `NEEDS CLARIFICATION`**.

Format: **Decision** / **Rationale** / **Alternatives considered**. Where several approaches work, the
simplest reliable one for a capstone was chosen.

---

## PL-01 — Guest basket merge rule (FR-BSK-01, FR-AUTH-08)

- **Decision**: Guest basket lives in `localStorage` (key `bookygeek.guestBasket`, array of
  `{bookId, quantity}` only — no prices). On login the client calls `POST /basket/merge`. The server merges
  **per book by summing quantities**, then caps each line at `min(10, physical stock)` (eBooks: 10).
  Books that are unlisted or physically out of stock are skipped. The response returns the merged basket
  plus a `report` (`added`, `adjusted`, `skipped` with reasons) shown to the customer as a message. The client
  clears `localStorage` only after a 2xx response.
- **Rationale**: Same cap logic as every other add-to-basket path (one implementation in the Cart module).
  Summation is the least surprising result for "add the same book twice".
- **Alternatives**: Keep the larger of the two quantities (loses intent); server basket wins (loses guest
  choices); guest basket wins (overwrites persisted basket). All rejected as more surprising.
- **Known/accepted risk**: a lost response followed by a retry would add quantities twice; the 10/stock cap
  bounds the effect and the customer can edit the basket. No idempotency token is added (YAGNI).
- **Guest pricing**: a guest basket page needs prices, so a public `POST /basket/preview` accepts
  `[{bookId, quantity}]` and returns priced lines and totals using the **same pricing code** as the
  customer basket. The browser never calculates money.

## PL-02 — Rounding (FR-RWD-03, FR-BSK-05)

- **Decision**:
  - Money is `NUMERIC(12,2)` in PostgreSQL, `BigDecimal` in Java, and a **decimal string** (`"1090.00"`) in
    the API (no binary floating point on the wire).
  - `tax = subtotal × 0.05`, rounded **once per order** with `RoundingMode.HALF_UP` to 2 dp (never per line).
  - `delivery = 40.00` if any physical book is in the order, else `0.00`.
  - `eligibleTotal = subtotal + tax + delivery`.
  - Reward cap: `maxPoints = min(balance, floor(eligibleTotal × 0.5))` — **fractional cap rounds down to a
    whole point** (`RoundingMode.DOWN` on the 50% value). Points are whole integers; 1 point = ₹1.00.
  - `payable = eligibleTotal − pointsRedeemed`. Because the cap is ≤ 50%, `payable > 0` always.
- **Rationale**: HALF_UP is the conventional commercial rounding; round-down on the cap guarantees the 50%
  limit is never exceeded. Single rounding point makes totals reproducible in tests (spec example: ₹1,000.00
  → tax ₹50.00, delivery ₹40.00, total ₹1,090.00).
- **Alternatives**: Per-line tax rounding (totals depend on line count); HALF_EVEN (surprising to customers);
  rounding the cap up (could breach 50%). Rejected.

## PL-03 — JWT lifetime, storage, logout (FR-AUTH-03, FR-AUTH-05, OD-1)

- **Decision**:
  - Signed JWT (HS256, secret from `JWT_SECRET` env, ≥ 32 bytes), claims `sub` (customer id), `iat`, `exp`,
    `tv` (token version). **Lifetime 60 minutes, absolute, no refresh token.**
  - **Storage**: delivered in an `HttpOnly`, `SameSite=Strict`, `Secure` (flag via env; off only for plain-HTTP
    local profile), `Path=/api` cookie `BG_AUTH`. The SPA never reads the token (not in `localStorage`).
    Spring Security resolves it with a small custom `BearerTokenResolver` that reads the cookie.
  - **CSRF**: because a cookie carries the credential, Spring Security CSRF protection stays enabled using the
    documented SPA recipe (`CookieCsrfTokenRepository.withHttpOnlyFalse()` + `XSRF-TOKEN` cookie + `X-XSRF-TOKEN`
    header). Axios sends the header automatically from its XSRF cookie/header settings.
  - **Same origin**: the nginx container proxies `/api` to the backend (Vite dev server does the same via
    `server.proxy`), so there is no cross-origin traffic in normal operation. CORS stays an **env allowlist**
    (`CORS_ALLOWED_ORIGINS`, empty by default).
  - **Logout**: `POST /auth/logout` increments `customer.token_version` (so every previously issued token fails
    validation) **and** expires the cookie. A custom `OAuth2TokenValidator` compares `tv` to the stored value
    (one primary-key lookup per protected request).
  - **Expiry mid-checkout**: Axios interceptor receives 401, clears client auth state and routes to
    `/login?returnTo=…` (A-12). The server-side basket survives (FR-BSK-09).
- **Rationale**: Security policy asks for httpOnly/sameSite session cookies; keeps XSS from stealing the token;
  `token_version` gives genuine invalidation without a deny-list table or Redis. No refresh flow keeps the
  design small.
- **Alternatives**: Bearer token in `localStorage` (simplest, but readable by any XSS); in-memory token (lost
  on refresh); `jti` deny-list table (extra table); refresh tokens (extra endpoints/state). Rejected.
- **Library**: Spring Security `oauth2-resource-server` (Nimbus JOSE) — part of the approved Spring Security
  addition family. Alternative `jjwt` (third-party) not needed.
- **Password hashing**: `Pbkdf2PasswordEncoder` (HmacSHA256, salted, iteration count per current OWASP/Spring
  defaults) inside a `DelegatingPasswordEncoder` so the algorithm can be upgraded. Chosen over BCrypt because
  BCrypt silently truncates at 72 **bytes** while the approved rule allows up to 64 **characters** (multi-byte
  characters could exceed 72 bytes); Argon2 would need an extra crypto library. Login performs a dummy hash
  check when the e-mail is unknown so response behaviour does not reveal which part was wrong (FR-AUTH-04).
- **E-mail**: trimmed and lower-cased (`Locale.ROOT`) before storage and lookup; unique constraint on the
  normalised value gives the case-insensitive uniqueness required by CL-18 without the `citext` extension.

## PL-04 — Brand (publisher) presentation (FR-CAT-02)

- **Decision**: A **"Brands" section in the left category sidebar**, below the category tree, listing
  publishers (with book counts). Single-select; selecting one sets `?brand=<publisher>` and combines with
  every other filter. On the book detail page, "Published by" links to the same brand filter. On mobile the
  sidebar (including Brands) opens from the header menu icon (CL-25, RSP-03).
- **Rationale**: Keeps the supplied filter row unchanged, adds no new header element (DS-07), reuses the
  existing sidebar toggle, and the sidebar is already the "browse by" area.
- **Alternatives**: A "Brand" dropdown in the filter row (changes the wireframe filter row); a separate
  `/brands` page (extra screen). Rejected.
- **Data**: brand = `book.publisher` (NOT NULL) — no brand table (spec §2.11). The brand list is a
  `SELECT DISTINCT publisher … ORDER BY publisher` over listed books.

## PL-05 — Seed lists (FR-CAT-01, FR-CAT-05, FR-CHK-02)

- **Categories** (hierarchy depth fixed at 2; books are attached to **leaf** categories; a parent lists the
  books of itself and its children):
  - **Fiction**: Romance, Mystery, Science Fiction, Fantasy, Historical, Young Adult, Children's,
    Comics & Graphic Novels, Poetry, Drama
  - **Non-Fiction**: Biography, Self Help, Memoir, Travel, Cooking, Science, Philosophy, Religion,
    Language Learning
  - This keeps all 19 wireframe categories as selectable leaves and provides the "Non-Fiction → Self Help"
    breadcrumb of the wireframe. "All" is a UI entry, not data.
- **Genre tag = category.** Genre tags on cards/detail are the book's leaf categories; selecting a tag applies
  the category filter. No separate genre entity (OD-12).
- **Formats** (CHECK-constrained): `PAPERBACK`, `HARDCOVER`, `EBOOK` (display "Paperback", "Hard Cover",
  "eBook"). Physical = anything except `EBOOK`.
- **Languages**: English, Hindi, French, Spanish (data-driven; the filter lists languages present in listed
  books).
- **Price bands** (served from application configuration, not a table): Under ₹200, ₹200–₹499, ₹500–₹999,
  ₹1,000 and above. API accepts `minPrice`/`maxPrice`; bands are labelled presets of those.
- **States**: the 28 states and 8 union territories of India as a classpath reference list served by
  `GET /reference/states`. When `country` is "India" (the default) the server requires `state` to be in the
  list; for any other country `state` is free text (max 60). Phone and PIN validations are as per CL-17.
- **Catalogue volume** (reproducible SQL seed): ≈ 60 books, ≥ 8 publishers, ≥ 13 books in at least one
  category (to exercise 12-per-page paging), all three formats, ≥ 2 languages, at least one physical book with
  stock 0, one with stock 1 (concurrency tests), one eBook. Covers are generated placeholder SVGs under
  `frontend/public/covers/` (no third-party artwork); `alt` text = "Cover of {title}".
- **Customers**: **no customer rows in seed data** (avoids committed credentials). Tests and demos register
  accounts. The 1,000-point starting balance is the committed, env-overridable property
  `bookygeek.rewards.initial-points=1000` applied when a customer registers.

## PL-06 — Dummy payment values (FR-PAY-04, FR-PAY-05, FR-PAY-08)

- **Card format**: `XXXX-XXXX-XXXX-XXXX` (16 digits, no Luhn check — keeps test values simple), CVV exactly 3
  digits, name on card 2–60 chars, expiry `MM/YYYY`, valid month, **not earlier than the current month** (clock
  injected).
- **Simulated success**: any well-formed card **except** the failure values. Documented sample:
  `4111-1111-1111-1111`.
- **Simulated failure (declined)**: card number `4000-0000-0000-0002` → `PAYMENT_DECLINED`.
- **UPI ID format**: `^[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9]{1,31}$`. Success sample `demo@upi`;
  **failure** ID is exactly `failure@upi` → `PAYMENT_DECLINED`.
- **COD**: no details; payment state `PENDING_ON_DELIVERY`, order `PLACED`.
- **Handling of card data**: PAN/CVV/expiry exist only in the request DTO in memory; the DTO overrides
  `toString` to mask, request-body logging is disabled, and nothing is persisted (not even last 4 digits).
  The failed attempt row stores only method, amount and a reason code.
- **Rationale**: deterministic, documented, no randomness → repeatable tests (FR-PAY-08).
- **Alternatives**: random failure rate (non-repeatable); failure keyed on amount (hidden coupling). Rejected.

## PL-07 — Relevance ordering and stock concurrency (FR-CAT-06, FR-ORD-08)

- **Relevance**:
  - With a search term: rank **0** title equals term (case-insensitive) → **1** title starts with term →
    **2** title contains term → **3** author contains term; ties: available (in-stock/eBook) first, then
    title A–Z, then id.
  - Without a search term: available first, then newest first (`created_at DESC`), then id.
  - Searching uses parameterised, wildcard-escaped `ILIKE` predicates (Spring Data JPA
    Specifications/Criteria; no native string concatenation). ~60 rows → no full-text engine.
- **Stock concurrency**: **pessimistic row locking** inside the order-placement transaction:
  `SELECT … FOR UPDATE` on the ordered physical `book` rows **in ascending id order** (prevents deadlocks),
  then check `stock ≥ qty`, then decrement. The reward-account row is also locked `FOR UPDATE`. DB `CHECK`
  constraints (`stock_quantity >= 0`, `balance >= 0`) are the last line of defence. Cancellation locks the
  order row, then the book rows (ascending), then the reward account.
- **Rationale**: deterministic, no retry loop, easy to prove with a two-thread integration test (spec: two
  buyers of the last unit → exactly one order).
- **Alternatives**: optimistic `@Version` with retry (retry logic, user-visible flakiness); serializable
  isolation (broader failures). Rejected.
- **Double submit (FR-PAY-11, FR-CNF-06)**: `POST /orders` requires an `Idempotency-Key` (UUID) header;
  uniqueness `(customer_id, idempotency_key)` lives on the `payment` row (one row per attempt). A repeated key
  returns the stored outcome instead of creating a second order. The front end keeps one key per Pay Now click
  sequence and generates a new key for a deliberate retry.

## PL-08 — Dependency vulnerability scanning (NFR-SEC-07)

- **Decision**: Policy names **Mend**; no decision selected a tool, and Mend needs org-level access that a
  capstone may not have. Adopt a two-tier rule:
  1. **Baseline (always available, no extra service)**: `npm audit --audit-level=high` for the front end,
     OWASP **Dependency-Check** Maven plugin (`dependency-check-maven`) for the back end, GitHub Dependabot
     alerts/updates on the repository. Reports are exported and attached to the delivery. Critical/high
     findings are patched or carry a written mitigation (constitution VIII).
  2. **Mend** is used instead of/in addition to (1) **if** the owner's account provides access.
- **Rationale**: free, scriptable, satisfies "Mend or an equivalent" (spec §5.1 note) and the 24h/30-day
  thresholds of the security rules.
- **Status**: the dependency-scanning tool is an *addition* (Principle III) — listed for owner sign-off in the
  plan's Additions table.

## PL-09 — Written Principle III proposals

Recorded in full in [plan.md → "Principle III Additions Register"](plan.md#principle-iii-additions-register-pl-09)
(purpose, alternatives, status for every addition and its companions). The constitution text itself is **not
edited** by the plan; a recommended optional PATCH amendment is listed in the plan's follow-ups.

## PL-10 — Approval gates

Two gate artifacts, both **blocking backend implementation** (not backend scaffolding, not front-end shell
work): `data-model.md` (reviewed) and `api/openapi.yaml` (authored contract-first, validated in Swagger
Editor, approved). Both are recorded in the plan's Gate Register and sequenced in plan Phase 2. Any later
change follows Principle IX (explicit revision with what/why/approver, same PR as the code).

---

## Additional technical decisions

### R-11 — Module dependency direction (Principle II)
Modules call each other only through public `*Service` classes, never repositories. Dependency graph (arrows =
"calls"): Order → Cart, Payment, Reward, User, Catalogue; Cart → Catalogue; Catalogue → Category&Brand
(`CategoryService.subtreeIds`); User → Reward (create account at registration); Category&Brand/`BrandService`
→ Catalogue (`listPublishers`). The last two edges form the only bidirectional pair (Catalogue ↔ Category &
Brand) because brand = a book attribute while the category tree is owned by Category & Brand; it is limited to
two named methods and recorded in Complexity Tracking. Recommendations live in the **Order** module (they
need order history; Order already depends on Cart and Catalogue), avoiding a cycle with Catalogue.

### R-12 — Pricing ownership
All money arithmetic (line totals, subtotal, tax, delivery, eligible total, payable, reward cap) lives in one
`PricingService` in the **Cart** module. Guest preview, customer basket, checkout quote and order placement all
call it; Order stores the resulting snapshot.

### R-13 — Delivery date
Computed in the Catalogue module with the injected `Clock` (zone `Asia/Kolkata`, configurable): physical =
today + 5 days, eBook = today. Returned as ISO date (`2026-07-21`); the UI formats as "Mon, 21 Jul" (`Intl`,
`en-IN`). Never stored. Tests use a fixed clock.

### R-14 — Revalidation before payment (FR-BSK-08)
`POST /checkout/quote` returns totals, `maxRedeemablePoints` and `issues[]` (`UNAVAILABLE`,
`INSUFFICIENT_STOCK`). `POST /orders` carries `expectedPayableTotal`; the server recomputes under locks and
returns `409 BASKET_CHANGED` if the figure or availability differs. The client re-fetches the basket, shows the
message, and the customer must press Pay Now again ("acknowledged").

### R-15 — Failed-payment recording
A declined payment is written in a separate (`REQUIRES_NEW`) transaction as a `payment` row with
`status=FAILED`, `order_id=NULL`, so the attempt is recorded (FR-PAY-12) while the order transaction rolls back
(stock, points, basket untouched). The table CHECK is `(FAILED ∧ order_id IS NULL) ∨ (non-FAILED ∧ order_id IS NOT
NULL)`, so a failed attempt can never carry an order and every other payment state always belongs to an order.

### R-15a — Reward ledger rows (RP-1 owner-approved: `reward_transaction` kept)
A `REDEEM` row is written only when `points_redeemed > 0`; a `RESTORE` row only when points are actually restored
(cancelled order had `points_redeemed > 0`). The `points > 0` CHECK is unchanged, so zero-point rows are impossible.
`UNIQUE (order_id, type)` is kept. RP-1 (keep the ledger) and RP-2 (no basket header table; basket = `basket_item`
rows) are **owner approved**.

### R-16 — Error model
One `ApiError` schema (`status`, `code`, `message`, `fieldErrors[]`, `timestamp`). 400 = malformed/invalid
input; 401 = unauthenticated; 404 = unknown **or not owned** resource (does not reveal existence); 409 =
valid request that conflicts with current state/business rules (stock, basket changed, points limit, window
expired, already cancelled, payment declined, duplicate e-mail). Generic messages only; details logged
server-side. Consequence: foreign-resource access answers 404 rather than 403 (still "denied"; FR-ORD-06).

### R-17 — Logging and observability
Spring Boot native structured logging (JSON, `logging.structured.format.console`), levels per profile, no
request-body logging, no secrets/PAN/CVV/passwords/tokens in log statements. No Actuator (not an approved
addition); Compose health check uses the public `GET /api/v1/categories`.

### R-18 — Integration-test database (Principle VII: "verify data is saved in PostgreSQL")
Use a dedicated **`postgres-test` service in Docker Compose** (profile `test`, port `127.0.0.1:5433`, throw-away
volume), Flyway-migrated, with a `test` Spring profile and a test-only cleanup step between tests that commit.
**Testcontainers is not adopted** (would be a new addition); H2 is rejected (not PostgreSQL; hides locking and
constraint behaviour).

### R-19 — Contract verification without extra libraries
Hand-written MockMvc tests assert status and body shape (JSON paths) per `operationId`; a checked-in
**traceability matrix** (endpoint ↔ contract ↔ test) satisfies NFR-API-04. An automatic response validator
(`swagger-request-validator`) is recorded as an **optional** addition (see Additions Register); the plan does not
depend on it.

### R-20 — Containers and registries (security rules §2/§3)
Images come from `registry.redhat.io`: build `ubi9/openjdk-21`, runtime `ubi9/openjdk-21-runtime`
(Java), `ubi9/nodejs-<latest LTS>` (front-end build), `ubi9/nginx-<latest>` (static + `/api` proxy),
`rhel9/postgresql-<latest supported>`. Exact tags are verified at scaffolding time (policy: latest, patched,
signed). Pulling requires a (free) Red Hat registry login — documented as a prerequisite. Containers run as
non-root, `read_only: true` root filesystem with `tmpfs` for writable paths, `cap_drop: [ALL]`,
`no-new-privileges`. Host ports are published on **`127.0.0.1` only**; the frontend is the only service a
browser needs. Inside the private Compose network the Spring and nginx listeners must accept connections on the
container interface (see Complexity Tracking); outside Compose (`mvn spring-boot:run`) the backend binds
`127.0.0.1`.

### R-21 — Version policy and Spring Boot generation (DECIDED — AMD-001 approved 2026-11-12)
Everything is "latest stable, actively maintained" at scaffold time and pinned in `pom.xml`/`package.json`
with committed lock files (`package-lock.json`; Maven dependency versions pinned through the Boot BOM and
explicit properties). The owner reported that no Spring Boot 3.x line has active OSS support, and the security rules
forbid EOL/extended-support-only software, so amendment
[AMD-001](amendments/AMD-001-spring-boot-4.md) moved the fixed stack to an actively supported Spring Boot **4.x**
(constitution v2.0.0, MAJOR; spec §5 and Revision 5 updated). The JUnit Jupiter version managed by Spring Boot 4 is
accepted under OD-8. The exact 4.x release is pinned in Phase 1 (newest patch of the supported line) after checking the
official support page and migration guide; items that may differ from Boot 3 (starter/package names, Spring Security
configuration API, Jackson 3, `@MockitoBean`) are verified then. Residual risk is recorded as **R-01** in the plan.
Planning decisions in this document do not depend on a specific Boot generation.

### R-22 — Frontend state and routing
React Context + hooks only (no Redux/Zustand). Two contexts: `AuthContext` (current customer, login/logout) and
`BasketContext` (one `useBasket()` API that transparently uses `localStorage` + `/basket/preview` for guests
and `/basket` for customers). Catalogue filters live in the URL query string (shareable, back-button safe).
A checkout draft (selected address, points) is kept in `sessionStorage`; card data never leaves component
memory. React Router: `/` catalogue, `/books/:id`, `/basket`, `/login`, `/register`, `/checkout`, `/payment`,
`/payment/confirmed/:orderId` (SCR-12), `/purchase/confirmed/:orderId` (SCR-09), `/orders`, `/orders/:id`,
`/account/addresses`, `*` not-found. Confirmation routes load by id, so refresh/back never create orders
(FR-CNF-06).

### R-23 — Frontend styling and fonts
Plain CSS: `tokens.css` (CSS custom properties — palette, spacing, type scale, breakpoints documented
640/1024), `base.css`, one `.css` per component with BEM-style class names. Font stack
`"IBM Plex Sans", "Segoe UI", system-ui, sans-serif` (uses the wireframe-like face when installed; no web-font
dependency or CDN). Dark theme only (OD-9).

### R-24 — Local ports
Frontend (nginx) `127.0.0.1:3000`; backend `127.0.0.1:8080`; PostgreSQL `127.0.0.1:5432`; test PostgreSQL
`127.0.0.1:5433`; Vite dev server `127.0.0.1:5173` (proxy `/api` → `127.0.0.1:8080`).
