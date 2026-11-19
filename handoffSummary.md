# BookyGeek – Handoff Summary (for a new chat)

## Goal
Implement the BookyGeek online bookstore for the Cloud Fullstack capstone.

- Stack: React + Vite SPA, Spring Boot 4.1.2 modular monolith, PostgreSQL 18, Docker Compose.
- Work follows `specs/001-bookygeek-platform/tasks.md` in dependency order, in the style of `/speckit-implement`.
- The task list has 155 tasks (T001–T155, 94 `[P]`). T145–T155 are the Phase 11 capstone submission tasks that were added from the capstone PPT review.
- Capstone deck: `Application Developer - Cloud FullStack - Capstone instructions.pptx`.

## Current state
- **Phases 1–6 are complete and ticked `[X]`.** They cover setup, foundation, US1 auth, US2 catalogue, US3 book detail and US4 basket (T083–T096).
- **Current focus: Phase 7, User Story 5 (Checkout, dummy payment, confirmation, T097–T114).**
  - The user said "continue", which I took as approval to start it.
  - Only the task list (`tasks.md` lines 222–252) and the schema have been read. **No Phase 7 file has been written yet.**
- Everything is uncommitted and unpushed. No commit or push is planned until Phase 11, and only with the owner's go-ahead.
- Not run: nothing ran against the Compose stack or other browsers because Docker is unavailable. T014, T046 and T135 are deliberately `[ ]` with STATUS notes.
- An `update_todo_list` call failed earlier because completed items from the old list must be preserved. When retrying, include the old completed items, or skip the todo list.

## Instructions and constraints
**Scope and architecture**
- Follow `tasks.md` in dependency order. Tick completed tasks `[X]` and add short STATUS notes. Report completed and blocked tasks with reasons.
- Keep the modular monolith with 7 business modules (`user`, `reward`, `category`, `catalogue`, `cart`, `payment`, `order`) plus `common`. No microservices.
- Excluded features stay excluded: wishlist, wallet, coupons, reviews, author profiles, real payment gateway, "Bestsellers" and "New Launches". The `bannedStrings` test enforces this.
- Never expose secrets. Bind to localhost only. Containers run as non-root from `registry.redhat.io` images. Use the latest stable packages.

**Gates**
- G-PLAN, G-AMD-001, G-DM and G-API are all APPROVED. T022 is ticked.
- Do not edit `plan.md`, `constitution.md`, `spec.md` or `data-model.md`, nor change `api/openapi.yaml`, without the owner's say-so. Any contract change goes back to the owner.
- Do not start Phase 11 (T145+, including the git push and PR) without the owner's say-so. The owner must supply GitHub and evaluator details.
- Ask before starting a new phase. The user has so far answered "continue" each time.

**Server and command handling (user instructions, repeated emphatically)**
- `timeout_seconds` does NOT reliably stop commands. A command that waits on a long-running process hangs the tool, and the result shows `CANCELED`.
- Never run a command that waits on a server, and never wait on a process that doesn't exit.
- Start servers with `Start-Process ... -RedirectStandardOutput/-RedirectStandardError ... -WindowStyle Hidden`. A CANCELED result is normal because the process still starts. Verify with a separate short command: `netstat -ano -p tcp | Select-String "LISTENING" | Select-String ":(8080|5173|5433)\s"`.
- Start the backend and Vite separately, in different commands. Do not restart healthy services or duplicate processes.
- Use `netstat`, not `Get-NetTCPConnection`.
- For long jobs (`mvn`, Playwright), start them detached with output redirected to a log file. Check the log in a separate short call, using `Start-Sleep -Seconds N` with N of about 40 or less. `$p.WaitForExit(40000)` on a detached `cmd.exe` worked fine for E2E.
- Use PowerShell-compatible commands.

**Contract deviation (documented)**
- The contract uses 401 `UNAUTHENTICATED` for all security failures, including a missing or mismatching CSRF token, because 403 is not used.

**Approved business rules**
- Tax is 5% of the subtotal, once per order, HALF_UP.
- Delivery is ₹40 for physical and mixed orders, and ₹0 for eBook-only orders.
- Maximum redeemable points = `min(balance, floor(0.5 × (subtotal + tax + delivery)))`, with 1 point = ₹1.
- A new account starts with 1,000 points. Points are never earned.
- Delivery date is today + 5 days for a physical book and today for an eBook. Stock does not change it.
- Page size is fixed at 12.
- A basket line holds 1 to 10 units, and for a physical book never more than its stock.
- Adding to the basket reserves nothing.

## Environment discoveries
- Windows with Windows PowerShell 5.1, Node v24, JDK 26 (the POM targets Java 21).
- Maven is not on PATH. Use `$env:PATH="$env:USERPROFILE\.bookygeek-tools\apache-maven-3.9.16\bin;$env:PATH"` and call `mvn.cmd`. Pass `-D` options containing dots via `mvn.cmd --% ...`.
- Docker and Podman are not installed. Tools cannot write outside the workspace.
- `.gitignore` must be written with `[IO.File]::WriteAllText` using `UTF8Encoding($false)`.
- Repo files are LF (`tasks.md` and `TRACEABILITY.md` were detected as `crlf=False`). Detect the line ending before string replacement.

**Tool and PowerShell traps**
- `write_file` fails with "path parameter is required" if `path` is omitted. This happened twice, so always supply `path`. Check each result for "Created file".
- Never put JS template literals in expandable PowerShell here-strings. Use `write_file` or `apply_diff`.
- `.visually-hidden` already exists in `frontend/src/styles/base.css`.

## Local test database
- A throw-away PostgreSQL 18.6 cluster is at `%USERPROFILE%\.bookygeek-tools\pg` (data in `pg\data`). It listens on `127.0.0.1:5433`.
- Role `bookygeek_test`, password `<local throw-away password>` (local only, not in the repo), database `bookygeek_test`.
- **It is currently STOPPED, and so are the backend and Vite.**
- Start it with:
  ```powershell
  $t="$env:USERPROFILE\.bookygeek-tools"; $pg=(Get-ChildItem "$t\pg" -Recurse -Filter pg_ctl.exe | Select-Object -First 1).FullName; Start-Process $pg -ArgumentList '-D',"`"$t\pg\data`"",'-l',"`"$t\pg\pg.log`"",'-o','"-p 5433 -c listen_addresses=127.0.0.1"','start' -WindowStyle Hidden; Start-Sleep -Seconds 10
  ```
  Then check port 5433.
- Stop it with `Start-Process $pg -ArgumentList '-D',"`"$t\pg\data`"",'stop','-m','fast','-t','15' -WindowStyle Hidden -PassThru`, then `WaitForExit(20000)`.
- Env vars for tests and runs: `SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/bookygeek_test`, `SPRING_DATASOURCE_USERNAME=bookygeek_test`, `SPRING_DATASOURCE_PASSWORD=<local throw-away password, kept outside the repository>`.

## How to run things
**Backend**
- It takes about 35 seconds to listen on 8080.
- Run `java -jar backend\target\bookygeek-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev`. It needs `JWT_SECRET` (random base64 of 48 bytes), `COOKIE_SECURE=false` and the three datasource vars.
- Logs go to `%USERPROFILE%\.bookygeek-tools\backend.out.log` and `backend.err.log`.
- Stop it by killing the PID that owns port 8080, parsed from `netstat`.

**Vite**
- `Start-Process cmd.exe -ArgumentList '/c','npx vite --host 127.0.0.1' -WorkingDirectory frontend -RedirectStandardOutput ... -WindowStyle Hidden`.
- It proxies `/api` to 127.0.0.1:8080. Stop it by killing the port-5173 PID.

**Maven**
- Full build, detached: `Start-Process cmd.exe -ArgumentList '/c',"mvn.cmd -B -o clean verify > `"$t\mvn.log`" 2>&1" -WorkingDirectory backend -WindowStyle Hidden`.
- It needs PATH and the three datasource env vars set in the same command. It takes about 90 seconds.
- Check with `Select-String -Path "$t\mvn.log" -Pattern "Tests run:.*Skipped: \d+$|BUILD|ERROR\]"`. Read failures with `Select-String ... "Tests run:|FAIL|expected"`.
- Surefire excludes `**/contract/*Test.java`. Failsafe runs `**/*IT.java` and `**/contract/*Test.java`.

**Frontend**
- Run `npx vitest run --reporter=dot` in `frontend/`, and `npm run build`. Both ran quickly.
- Strip ANSI with `-replace '\x1b\[[0-9;]*m',''`. Vitest "Unhandled Errors" must be investigated, not ignored.

**Playwright**
- Run from `e2e/` as a detached `cmd.exe`: `set E2E_BASE_URL=http://127.0.0.1:5173&& npx playwright test --project=chromium --reporter=line --timeout=30000 > log 2>&1`. Then read the log tail.
- Remove `e2e/test-results` and `e2e/playwright-report` afterwards.
- Use `{ exact: true }` for `getByText('Total')` because it also matches "Subtotal". Scope locators to avoid cross-matching.
- `e2e/tmp-debug.spec.js` and one `e2e/test-results` folder were deleted externally. Both are fine.

## Frontend patterns
- Page tests wrap in `MemoryRouter` plus `AuthContext.Provider value={{customer:null, loading:false, login, logout}}` and `BasketContext.Provider`, because `PageShell` → `Header` → `AccountMenu` uses `useAuth`.
- Fixtures in `src/test/fixtures/index.js` include `paperback` (id 7, ₹500, stock 25), `ebook` (id 12), `outOfStock` (id 31), `bookDetail`, `ebookDetail`, `outOfStockDetail`, `bookPage`, `categories`, `brands`, `filterOptions`, `customer`, `address`, `emptyBasket`, `basket`, `checkoutQuote`, `order` and `apiErrors`.
- `useAsync(fn, deps)` returns `{data, error, loading, retry}`.
- Reusable components: `Button` (variants primary/secondary, `type="button"` default), `Spinner`, `ErrorMessage` (role=alert, optional Retry), `EmptyState`, `PageShell` (optional `categories`/`brands` sidebars).
- `ProtectedRoute` in `routes.jsx` sends a guest to `/login?returnTo=...`.
- These routes are still placeholder pages: `/checkout`, `/payment`, `/payment/confirmed/:orderId`, `/purchase/confirmed/:orderId`, `/orders`, `/orders/:id` and `/account/addresses`.
- Utilities:
  - `utils/money.js` has `formatMoney` and `formatPoints`.
  - `utils/date.js` has `formatDeliveryDate`.
  - `utils/validators.js` holds T027 validation.
  - `utils/returnTo.js` has `safeReturnTo`.
  - Design tokens are in `src/styles/tokens.css`.
- `bannedStrings.test.jsx` renders all routes as guest and as customer. Any new page must render with the mocked API fixtures, so add fixtures for any new GET or it throws unhandled errors.
- `BasketContext` has `addItem`, `updateItem`, `removeItem`, `refresh`, `mergeReport` and `dismissMergeReport`. After placing an order, call `refresh` so the basket is cleared.

## Backend patterns
- A controller uses `@AuthenticationPrincipal AuthenticatedCustomer customer` and `customer.id()` (from `com.bookygeek.common.security`).
- `@Valid @RequestBody` records carry validation messages. A path variable can carry `@PathVariable @Min(...)`.
- Errors are thrown as `new ApiException(ErrorCode.X)`. The set is closed: `VALIDATION_FAILED`, `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `EMAIL_TAKEN`, `NOT_FOUND`, `BOOK_NOT_FOUND`, `QUANTITY_LIMIT`, `OUT_OF_STOCK`, `EMPTY_BASKET`, `BASKET_CHANGED`, `POINTS_LIMIT_EXCEEDED`, `PAYMENT_DECLINED`, `CANCELLATION_WINDOW_EXPIRED`, `ALREADY_CANCELLED`, `INTERNAL_ERROR`.
- `ApiException` can carry field errors. `GlobalExceptionHandler` builds the `ApiError` body.
- `SecurityConfig` permits `POST /basket/preview` publicly. Everything else under `/api/v1` needs authentication plus a CSRF header for state-changing calls.
- Services are concrete classes (no interface-per-service).
- `ModuleBoundaryTest` enforces:
  - No class uses another module's `repository` or `entity` types.
  - Services may use other modules' services and DTOs only, except the approved catalogue↔category pair.
  - Controllers never touch repositories.
- For Phase 7, `order` must call `CartService`, `PricingService`, `RewardService`, `PaymentService` and `CatalogueService` through services only.
  - Stock locking (`SELECT … FOR UPDATE`, books in ascending id order) and stock decrement must go through a **new service method in the catalogue module** that returns DTOs, because `order` may not use `BookRepository` or `Book`.
  - Check `ModuleBoundaryTest` from line 153 (`onlyCatalogueAndCategoryMayDependOnEachOther`) first. In Phase 6, cart→catalogue passed because only non-service, non-dto cross-module types are flagged.
- `RewardService`:
  - Methods: `createAccount`, `getBalance`, `lockAccount` (FOR UPDATE), `deduct(customerId, orderId, points)` and `restore`.
  - `deduct` is a no-op for 0, throws `POINTS_LIMIT_EXCEEDED` above the balance, and writes a REDEEM row.
  - Its methods join the caller's transaction.
- `PricingService` has `price(List<PricedLine>)` returning `Totals(subtotal, tax, deliveryCharge, total)`, `maxRedeemablePoints(balance, total)`, and a static `format(BigDecimal)`.
- `CartService` has `getBasket`, `preview`, `addItem`, `updateItem`, `removeItem` and `merge`.
  - **`clear(customerId)` was probably never added.** Check `CartService.java` and add it for `placeOrder` (T109) if it is absent.
- `BookygeekProperties` holds `rewards` (initialPoints, capPercent), `tax.rate`, `delivery.charge`, `cancellation.windowHours` and `priceBands`.
- `IntegrationTestBase` provides `mockMvc` and `jdbc`. It truncates customer, address, reward_account, reward_transaction, basket_item, orders, order_item and payment before each test, and restores book stock. `JWT_SECRET` is a random per-run value.
- Test helper pattern (see `BasketIT` and `BasketContractTest`):
  - Register through `POST /api/v1/auth/register`, then log in and take the `BG_AUTH` cookie.
  - Add `new Cookie("XSRF-TOKEN", "csrf-token-for-tests")` and the header `X-XSRF-TOKEN` to state-changing calls.
  - `ContractSupport.assertResponse(operationId, status, body)` validates against the contract and is closed (no undeclared property).
- `AuthController.register` returns 201 with a Location header.

## Schema (V1__init_schema.sql, Phase 7 tables)
- `address`:
  - Columns: `id`, `customer_id` (FK, ON DELETE CASCADE), `full_name` (100), `phone CHAR(10)` (CHECK `^[0-9]{10}$`), `address_line1` (150), `address_line2` (150, nullable), `city` (80), `state` (60), `pin_code CHAR(6)` (CHECK `^[0-9]{6}$`), `country` (56, default 'India'), `created_at`, `updated_at`.
- `orders`:
  - Columns: `id`, `customer_id`, `status` ('PLACED'|'CANCELLED'), `placed_at`, `cancelled_at`.
  - Shipping snapshot: `ship_full_name`, `ship_phone`, `ship_line1`, `ship_line2`, `ship_city`, `ship_state`, `ship_pin_code`, `ship_country`.
  - Money: `subtotal`, `tax`, `delivery_charge` (NUMERIC(12,2)), `points_redeemed` (default 0), `payable_total`.
  - CHECKs: `ck_orders_cancelled_at`; `2*points_redeemed <= subtotal+tax+delivery_charge`; `payable_total = subtotal+tax+delivery_charge-points_redeemed AND payable_total > 0`.
- `order_item`: PK `(order_id, book_id)`, `quantity` 1..10, `unit_price` > 0.
- `payment`:
  - Columns: `id`, `customer_id`, `order_id` (nullable, UNIQUE), `method` ('CREDIT_CARD'|'DEBIT_CARD'|'UPI'|'COD'), `status` ('PAID'|'PENDING_ON_DELIVERY'|'FAILED'|'REFUNDED'|'CANCELLED'), `amount`, `failure_code`, `idempotency_key UUID`, `created_at`, `updated_at`.
  - UNIQUE `(customer_id, idempotency_key)`. CHECK `(status='FAILED' AND order_id IS NULL) OR (status<>'FAILED' AND order_id IS NOT NULL)`.
- `reward_transaction`: `order_id` FK to `orders`, UNIQUE `(order_id, type)`.

## Contract operations for Phase 7 (`api/openapi.yaml`, read the exact schemas before coding)
- `listAddresses` (`/addresses`, line 194), `createAddress` (236), `updateAddress` and `deleteAddress` (`/addresses/{id}`, 287–344), `listStates` (`/reference/states`, 358).
- `quoteCheckout` (`/checkout/quote`, 1011), `placeOrder` (`POST /orders`, 1139, needs the `Idempotency-Key` UUID header), `getOrder` (`/orders/{id}`, 1276).
- Schemas `CheckoutQuote` (about line 1997), `CheckoutQuoteRequest` (`pointsToRedeem`) and `CheckoutIssue` (about 1983) are near it.
- `CardDetails` (about 2047) is the card format `XXXX-XXXX-XXXX-XXXX` with `nameOnCard`, `cvv` and `expiry`.
- Order schemas and `PlaceOrderRequest` are further down and have not been read yet.
- Error examples (`ErrQuantityLimit`, `ErrOutOfStock`, `ErrNotFound`, `ErrBookNotFound` and others) are near the end of the file. `OrderPlacedPaid` is an example near line 2414.

## Simulator rules (T097, PL-06; re-read `spec.md` and `plan.md` before coding)
- Card `4111-1111-1111-1111` approves and `4000-0000-0000-0002` declines. Any other valid card is declined per PL-06.
- UPI `demo@upi` approves and `failure@upi` declines. COD gives `PENDING_ON_DELIVERY` with no details.
- Raw card data is never stored or logged.
- A `FAILED` payment row is written via `REQUIRES_NEW` with `order_id` NULL, and the rest of the transaction rolls back.

## Seed data
- 60 books, none unlisted. Book 5 is the only physical book with stock 0.
- Book 1 is "The Habit Ladder" (₹499, stock 25, Self Help category 14). Book 13 "The Two-Minute Reset" is an eBook (₹99).
- Book 39 is the only Drama book. Book 40 is the most expensive (₹1299).

## Accomplished
**Phases 1–6 verified**
- Backend: `mvn clean verify` ended BUILD SUCCESS with 110 unit tests and 117 integration/contract tests.
- Frontend: 257 Vitest tests pass and `npm run build` succeeds.
- E2E: E2E-01..04 pass on Chromium (13 tests, 3 consecutive runs).

**Phase 6 deliverables** (all ticked in `tasks.md`, with a Phase 6 STATUS note before the "Checkpoint: Basket complete" line; `api/TRACEABILITY.md` rows 16–21 are `implemented`)
- Backend, in `cart/`: `BasketItem` (composite key via `@IdClass(BasketItem.Key)`), `BasketItemRepository`, `PricingService`, `CartService`, `BasketController`, and DTOs `BasketLine`, `BasketResponse`, `BasketLineRequest`, `BasketLinesRequest`, `AddBasketItemRequest`, `UpdateBasketItemRequest`, `BasketAdjustment`, `MergeReport`, `MergeResult`, `PricedLine`, `Totals`.
- Catalogue module: added `CatalogueService.listedSummaries(Collection<Long>)`.
- Frontend: `api/basketApi.js`, `hooks/useBasket.js` (adds `quantityOf`), `components/basket/AddToBasketButton.{jsx,css}`, `pages/BasketPage.{jsx,css}`. `BasketContext.jsx` was rewritten to use `basketApi` and to check stock for guests. `routes.jsx` points `/basket` at `BasketPage`. Add to Basket was added to `BookCard` and `BookDetailPage`.
- Tests added: `PricingServiceTest`, `CartServiceTest`, `BasketIT`, `BasketContractTest`, `BasketPage.test.jsx`, `BasketContext.merge.test.jsx`, `useBasket.test.js`, `e2e-04-basket.spec.js`.
- Tests updated: `BookCard.test.jsx`, `BookDetailPage.test.jsx`, `bannedStrings.test.jsx`, `e2e-03-detail.spec.js`.

## Next steps (Phase 7 plan)
1. Read the remaining contract schemas and the PL-06, payment and checkout sections of `spec.md` and `plan.md`.
2. **T105** — address module in `user/`: `Address`, `AddressRepository`, `AddressRequest`/`AddressResponse`, `AddressService`, `AddressController`. `listStates` reads from `reference/states.json` (find where that file lives).
3. **T106** — `payment/`: `Payment`, `PaymentRepository`, `PaymentRequest`, `PaymentService`.
4. **T107** — `order/` entities and repositories: `OrderEntity`, `OrderItem`, `OrderRepository`, `OrderItemRepository`.
5. **T108–T110**, in `order/`:
   - **T108:** `CheckoutQuoteService` and the DTOs `CheckoutQuote`, `PlaceOrderRequest` and `OrderResponse`.
   - **T109:** `OrderService.placeOrder`, one `@Transactional` unit following the task text. It needs the catalogue stock lock and decrement service method, and `CartService.clear`.
   - **T110:** `CheckoutController` and `OrderController` (`getOrder`).
6. **Backend tests:** T097 `PaymentServiceTest`, T098 `OrderServiceTest`, T099 `PlaceOrderIT`, T100 `StockConcurrencyIT`, T101 `AddressIT`, T102 `CheckoutContractTest` and `AddressContractTest`.
7. **Frontend (T111–T113, T103):**
   - New API files: `api/addressApi.js` and `api/orderApi.js`.
   - New pages: `AddressesPage`, `CheckoutPage`, `PaymentPage`, `PaymentConfirmedPage` (not shown for COD) and `PurchaseConfirmedPage`, with CSS.
   - Wire them into `routes.jsx` as protected routes. Send an `Idempotency-Key` per attempt, and clear card fields on failure.
   - Add page tests and `bannedStrings` fixtures for the new GETs.
8. **E2E (T104):** `e2e-05-checkout.spec.js` and `e2e-06-payment-failure.spec.js`.
   - E2E-05: each of CC, DC, UPI and COD completes, with payment confirmation except for COD, then purchase confirmation, an empty basket, and the order in My Orders.
   - E2E-06: declined card/UPI changes nothing, then a retry succeeds.
   - My Orders is Phase 8, so E2E-05 may need to check the order through the API (`getOrder`) until then.
9. **T114:** full `mvn clean verify`, vitest, build and E2E-01..06. Then:
   - Tick T097–T114 and add the Phase 7 STATUS note before the "Checkpoint: Full purchase journey works" line.
   - Update the `TRACEABILITY.md` rows for `listAddresses`, `createAddress`, `updateAddress`, `deleteAddress`, `listStates`, `quoteCheckout`, `placeOrder` and `getOrder`. Read the file for the row format, and note `getOrder` may be shared with Phase 8.
   - Stop all services, remove the e2e result folders and check `git status`.
10. Report to the owner and ask before starting Phase 8 (T115 onward: orders, cancellation, Buy Again, recommendations).

## Not started
- Phase 7 code, Phases 8–10, and Phase 11 (T145–T155: manual API testing, frontend code review, local UI verification, GitHub push and PR, demo video, optional cloud deployment).
- Phase 11 starts only on the owner's explicit go-ahead.

## Relevant files and directories
**Governing docs** (read-only unless noted)
- `specs/001-bookygeek-platform/tasks.md` (edit ticks and notes). Phase 7 is lines 222–252 (T097–T114), and Phase 8 starts at line 256.
- `plan.md`, `spec.md`, `data-model.md`, `research.md`, `quickstart.md`, `contracts/api-contract-outline.md`
- `api/openapi.yaml` (approved, do not change)
- `api/TRACEABILITY.md` (edit rows to `implemented` when done)

**Backend (`backend/`)**
- `pom.xml`
- `src/main/java/com/bookygeek/`:
  - `common/{error,props,security,time}/*`
  - `user/*`, `reward/{entity,repository,service}/*`, `category/*`
  - `catalogue/{entity,repository,dto,service,controller}/*`
  - `cart/{entity,repository,dto,service,controller}/*`
  - `payment/` and `order/` (empty, to be created in Phase 7)
- `src/main/resources/{application*.yml,db/migration/V1__init_schema.sql,db/seed/*}`
- `src/test/java/com/bookygeek/`:
  - `unit/{catalogue,category,user,reward,common,cart}/*`
  - `integration/{IntegrationTestBase,SecurityBaselineIT,AuthFlowIT,CatalogueQueryIT,BookDetailIT,BasketIT}.java`
  - `contract/{ContractSupport,AuthContractTest,CatalogueContractTest,BasketContractTest}.java`
  - `architecture/ModuleBoundaryTest.java`

**Frontend (`frontend/src/`)**
- `api/{axiosClient,catalogueApi,basketApi}.js`
- `context/{AuthContext,BasketContext}.jsx`
- `hooks/{useAsync,useCatalogueQuery,useBasket}.js`
- `utils/{money,date,bookLinks,storage,returnTo,validators}.js`
- `components/{basket,book,catalogue,common,layout}/*`
- `pages/{CataloguePage,BookDetailPage,BasketPage,LoginPage,RegisterPage,NotFoundPage,PlaceholderPage}.jsx`
- `routes.jsx`, `test/{fixtures/index.js,bannedStrings.test.jsx}`, `__tests__/routes.test.jsx`
- `frontend/public/covers/` holds the covers.

**E2E (`e2e/`)**
- `playwright.config.js` and `tests/e2e-01-auth.spec.js` through `e2e-04-basket.spec.js`. `e2e-05` and `e2e-06` are still to be created.

**Outside the repo:** `%USERPROFILE%\.bookygeek-tools\` holds `apache-maven-3.9.16\`, `pg\` (PostgreSQL cluster), and the logs `mvn.log`, `backend.out.log`, `backend.err.log`, `vite.out.log`, `vite.err.log`, `e2e.log`.

**Git:** everything is unstaged and uncommitted. The untracked tree includes `backend/`, `frontend/`, `e2e/`, `api/`, `docs/`, `.github/`, `.gitignore`, `.env.example`, `README.md`, `docker-compose.yml` and the spec files. A stray `bob-task-*.md` file in the repo root is not mine.
