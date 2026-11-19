# BookyGeek – NFR, responsive, accessibility and wireframe verification (T139)

Date: 2026-11-16. Everything below was measured or observed on this machine. Anything that was not run is listed in section 9 and is **not** claimed as passed.

## 1. Scope and method

| Item | Value |
|------|-------|
| Stack under test | Spring Boot jar (`SPRING_PROFILES_ACTIVE=dev`, seed data, 60 books) + PostgreSQL 18.6 + Vite (dev server `:5173`, production build via `vite preview` `:4173`), all on loopback |
| Not the stack under test | The Docker Compose stack (nginx frontend on `:3000`). Docker and Podman are not installed here, so the "local Docker Compose" target of NFR-PERF-01/02 is approximated, not measured (see section 9) |
| Browser for measurements | Playwright Chromium 158.0.8127.18 |
| Harness | [`docs/nfr-evidence/measure-nfr.cjs`](nfr-evidence/measure-nfr.cjs) (Node + Playwright). `NFR_ONLY=keyboard` runs the keyboard journey alone; `NFR_PREVIEW` and `NFR_DEV` override the two frontend URLs |
| Raw results | `nfr.json` written by the harness outside the repository (not committed) |
| Screenshots | 56 JPEGs in [`docs/screenshots/`](screenshots/), about 2.2 MB in total, named `<screen>-<width>.jpg` for 1280, 1024, 640 and 375 px |

Limits of the method:

- Timings are loopback timings on a developer machine. They show headroom against the 1 s and 3 s targets; they are not a load test.
- The accessibility audit is a custom in-page script, **not axe-core** and not a complete WCAG audit. No screen reader was used.
- The focus-indicator check is a heuristic (computed style on focus differs from blur), not a visual review.

## 2. NFR-PERF-01 – typical API response under 1 second

Method: every one of the 29 operations in `api/openapi.yaml` was called once through the real HTTP API with the browser session cookies. Read-only GETs were repeated 10 times. `placeOrder` was exercised as Cash on Delivery.

Result: **PASS on this machine.** The slowest single call was `register` at 126.8 ms. All other operations were under 125 ms; every operation was well under 1000 ms.

| Operation | Method and path | Status | First call (ms) | Median / max of 10 (ms) |
|-----------|-----------------|-------:|----------------:|------------------------:|
| listStates | GET /reference/states | 200 | 21.3 | 3.3 / 5.5 |
| listCategories | GET /categories | 200 | 4.2 | 2.2 / 4.5 |
| listBrands | GET /brands | 200 | 1.8 | 2.7 / 3.2 |
| listBooks | GET /books | 200 | 5.3 | 5.4 / 6.2 |
| listBooks (search, filters, sort) | GET /books?q=the&language=English&sort=PRICE_ASC | 200 | 7.1 | 6.4 / 8.3 |
| getBookFilterOptions | GET /books/filter-options | 200 | 2.0 | 1.9 / 3.3 |
| getBook | GET /books/1 | 200 | 3.1 | 3.0 / 5.2 |
| listRelatedBooks | GET /books/1/related | 200 | 6.2 | 5.4 / 6.2 |
| previewBasket | POST /basket/preview | 200 | 5.6 | single sample |
| register | POST /auth/register | 201 | 126.8 | single sample |
| login | POST /auth/login | 200 | 111.3 | single sample |
| getCurrentCustomer | GET /auth/me | 200 | 3.3 | 3.2 / 4.3 |
| listAddresses | GET /addresses | 200 | 2.8 | 2.8 / 4.4 |
| getBasket | GET /basket | 200 | 3.8 | 4.0 / 5.6 |
| getRewardAccount | GET /rewards/me | 200 | 5.2 | 3.8 / 5.5 |
| listOrders | GET /orders | 200 | 5.4 | 4.2 / 5.5 |
| getBasketRecommendations | GET /recommendations | 200 | 4.3 | 4.4 / 5.8 |
| createAddress | POST /addresses | 201 | 6.1 | single sample |
| updateAddress | PUT /addresses/{id} | 200 | 6.4 | single sample |
| addBasketItem | POST /basket/items | 200 | 9.4 | single sample |
| mergeGuestBasket | POST /basket/merge | 200 | 11.9 | single sample |
| updateBasketItem | PUT /basket/items/{bookId} | 200 | 12.1 | single sample |
| removeBasketItem | DELETE /basket/items/{bookId} | 200 | 6.4 | single sample |
| quoteCheckout | POST /checkout/quote | 200 | 6.8 | single sample |
| placeOrder (Cash on Delivery) | POST /orders | 201 | 20.5 | single sample |
| getOrder | GET /orders/{id} | 200 | 5.4 | single sample |
| buyAgain | POST /orders/{id}/buy-again | 200 | 21.8 | single sample |
| cancelOrder | POST /orders/{id}/cancellation | 200 | 15.1 | single sample |
| deleteAddress | DELETE /addresses/{id} | 204 | 7.7 | single sample |
| logout | POST /auth/logout | 204 | 5.1 | single sample |

Caveats: write operations are single samples, the first call includes JIT and connection warm-up, and `placeOrder` was measured for Cash on Delivery only (card and UPI placement go through the same service plus the dummy payment simulation, but were not timed separately).

## 3. NFR-PERF-02 – initial frontend load under 3 seconds

Method: 5 runs per route and target, fresh browser context with an empty cache each time, 12 books on the catalogue.

| Target | Route | Load event median / max (ms) | First book card visible, median / max (ms) | Transferred |
|--------|-------|-----------------------------:|-------------------------------------------:|------------:|
| Production build (`vite preview`) | `/` | 48.0 / 52.6 | 147 / 161 | about 130 KB |
| Production build (`vite preview`) | `/books/1` | 48.7 / 51.7 | 128 / 143 | about 130 KB |
| Dev server (`vite dev`) | `/` | 154.8 / 157.5 | 282 / 288 | about 6.2 MB |
| Dev server (`vite dev`) | `/books/1` | 149.4 / 158.7 | 250 / 260 | about 6.2 MB |

Result: **PASS on this machine**, by a large margin (worst case 0.29 s against a 3 s target).
Caveat: neither target is the Compose nginx container the spec names. A production build is what Compose serves, so the first two rows are the closer proxy.

## 4. NFR-ACC-01 – accessibility basics

### 4.1 Static audit of 14 screens at 1280 px

Screens: catalogue, book-detail, login, register, not-found, basket-empty, basket, checkout, payment, payment-confirmed, purchase-confirmed, orders, order-detail, addresses.

| Check | Result |
|-------|--------|
| Form controls without an accessible label | 0 |
| Buttons and links without an accessible name | 0 |
| Visible images without `alt` | 0 |
| Text nodes checked for contrast | 521 across the 14 screens |
| Text below WCAG AA (4.5:1, or 3:1 for large text) | 0; lowest ratio measured 4.81:1 (My Orders and order details) |
| Text with an undeterminable background | 0 |
| Focus-indicator gaps across the first 25 Tab stops per screen | 0 (heuristic) |
| `<html lang>` and one `<main>` landmark per screen | present on all 14 |
| `<h1>` per screen | 1 on 13 screens; **0 on the not-found screen** (see observations) |

### 4.2 Keyboard-only journey

All 10 steps passed with the keyboard only (Tab, Shift+Tab, Enter, Space, arrow keys, Escape):

1. Log in with E-mail, Password and Enter (5 Tab presses)
2. Add a book to the basket (47)
3. Open the basket from the header (51)
4. Proceed to Checkout (41)
5. Fill the address, including the State list (30)
6. Choose Cash on Delivery with the arrow keys on the radio group and press Pay Now (25)
7. Pay Now on the payment page reaches the purchase confirmation (1)
8. Open My Orders from the header (2)
9. Open the cancel dialog; focus moves into it; Escape closes it (9)
10. Cancel the order from the dialog with Enter

### 4.3 Observations (not failures of the stated criteria, recorded for the owner)

- **No "skip to content" link.** The catalogue needs 47 to 51 Tab presses to reach the first action because the header and category sidebar come first.
- **The not-found page has no `<h1>`.** It renders "Page not found" as an `<h2>` through the shared empty-state component.
- The audit is a custom script. A full WCAG review with axe-core or a manual screen-reader pass was **not** done.

Result: **the NFR-ACC-01 criteria (labels, contrast, alt text, keyboard-operable journey) are met on the 14 screens audited**, with the observations above and without a screen-reader test.

## 5. Responsive behaviour (RSP-01 to RSP-04)

Method: 14 screens at 6 widths (1280, 1024, 1023, 640, 639 and 375 px) = 84 combinations. For each, `document.scrollWidth` was compared with `window.innerWidth` and the console was watched for errors.

Result: **0 of 84 combinations overflow horizontally; 0 console errors.**

| Breakpoint | Widths checked | Observed layout (from the screenshots at 1024, 640 and 375 px; 1280 px column count measured) |
|------------|----------------|-----------------|
| Desktop (1024 and above) | 1280, 1024 | 1280 px: category rail plus three catalogue columns (measured at 1280, 1440 and 1600 px). 1024 px: category rail visible, **two** catalogue columns |
| Tablet (640 to 1023) | 1023, 640 | 640 px: one catalogue column, category rail behind a menu button, filter row visible, Related Reads below the book details in a four-column grid, checkout stacked in one column |
| Mobile (below 640) | 639, 375 | 375 px: one catalogue column, category rail behind a menu button, **filter row (Language, Format, Price, Sort) visible, not behind a toggle**, forms usable |

Differences from the spec text, for the owner:

- RSP-03 asks for the filter row behind a toggle on mobile. It is always visible; only the category rail is behind a toggle.
- RSP-01 asks for three card columns on desktop. This holds at 1280 px and above, but at exactly 1024 px the grid has two columns because the rail takes width.
- Only the 1024, 640 and 375 px screenshots of the catalogue, plus 640 px book details and checkout, were viewed individually for this section. The remaining screens were covered only by the automated overflow check.

RSP-04 (equivalent function at every size) was exercised by the keyboard and E2E journeys at desktop size; the 84-combination sweep checks layout and overflow, not that every action was re-performed at 375 px.

## 6. NFR-BRW-01 – browsers

The full E2E suite (E2E-01 to E2E-08, 31 tests) was run against the dev server on `:5173` after the CSS fixes in section 7:

| Browser | Result |
|---------|--------|
| Playwright Chromium 158 | 31 of 31 passed (46.8 s, 3 workers) |
| Microsoft Edge 157 (installed Edge, Playwright `channel: 'msedge'`, 2 workers, throw-away config not committed) | 31 of 31 passed (43.1 s) |
| Playwright Firefox | 31 of 31 passed in an earlier run (1 worker). With 3 workers two tests timed out under load and passed when run alone; no code was changed for them |
| Playwright WebKit (extra, not in the spec) | 31 of 31 passed in an earlier run |

Not covered: **Google Chrome itself is not installed**; Playwright Chromium and Edge are both Chromium builds and are the proxy for it. "Latest version" was taken as the version installed or downloaded today, not independently verified against vendor release pages. The Chromium and Edge results above were re-run after the CSS fixes; the Firefox and WebKit results pre-date them (the CSS changes are layout-only and no functional code changed).

## 7. Wireframe comparison (SC-003, SCR-04/05/07/08/09)

The five supplied wireframes in `Capstone_frontend_images/` were viewed against the 1280 px screenshots in `docs/screenshots/`.

### 7.1 Defects found and fixed (CSS only, behaviour-neutral)

| File | Defect | Fix | Verified |
|------|--------|-----|----------|
| [`frontend/src/pages/CataloguePage.css`](../frontend/src/pages/CataloguePage.css) | The card grid minimum of 24 rem produced **2** columns at 1280 px; SCR-04 and RSP-01 require three | Minimum track width reduced to 20 rem. Three columns measured at 1280, 1440 and 1600 px; the 639 px single-column rule is unchanged | Screenshot re-taken; 362 of 362 frontend tests, build and 31 of 31 E2E pass |
| [`frontend/src/pages/BookDetailPage.css`](../frontend/src/pages/BookDetailPage.css) | The two covers stretched to the height of the Related Reads column (about 144 by 383 px strips) | `align-items: flex-start` on `.book-detail__covers`; covers are now 144 by 216 px (2:3) | Screenshot re-taken |

### 7.2 Approved deviations (not defects)

No Wishlist, My Writers, ratings, reviews, "About the writer" or coupon; no Wallet; brand name "BookyGeek"; no Bestsellers or New Launches; Cash on Delivery added as a fourth payment method; no footer.

### 7.3 Visible differences that remain – open points for the owner

| Screen | Criterion | Observation | Status |
|--------|-----------|-------------|--------|
| SCR-04 Catalogue | Header, category tree with highlight, filter row, three columns, 12 per page, dark theme | All present. Cards show stock and "Add to Basket" and do not follow a fixed three-section layout. "Recommended for You" is built and appears only for customers with order history | Met, with a card-layout difference |
| SCR-05 Book details | Breadcrumb, two covers, details, blue Add to Basket, Related Reads in a right column | Present after the cover fix. No excluded elements | Met |
| SCR-07 Checkout | Breadcrumb, basket panel with steppers, address form and saved addresses, summary, Pay Now, rewards below summary, no coupon | All elements present. Layout is two columns (basket and address form on the left, summary and rewards on the right) rather than the wireframe's three side-by-side panels (RSP-01 "three-panel checkout") | **Partial: layout differs from wireframe** |
| SCR-08 Payment | **Illustrated background**, title and amount, method list with selected accent, card fields, Pay Now, no Wallet | No illustrated background. Methods are a 2 by 2 radio grid rather than a vertical list with a left accent | **Not met: illustrated background; list layout differs** |
| SCR-09 Purchase confirmation | Success tick, message, book summaries, Continue-your-Shopping, **illustrated background** | No illustrated background. Rendered as a full-width, left-aligned page with a "Purchase confirmed" heading (not a centred dialog). Order summary, address and payment panels are additional | **Not met: illustrated background; layout differs** |
| SCR-01, 02, 03, 06, 10, 11, 13 | Consistent with DS-01..DS-12, usable, responsive | Checked for consistency with SCR-04 and SCR-07 and in the 84-combination sweep. No wireframe exists for them | Consistency checked only |

**SC-003 is not claimed as fully met.** The illustrated backgrounds on SCR-08 and SCR-09, the two-column (not three-panel) checkout and the full-width confirmation page are decisions for the owner: accept as deviations, or request changes.

## 8. Result summary

| Requirement | Result |
|-------------|--------|
| NFR-PERF-01 | Met on this machine (all 29 operations under 130 ms; target 1 s). Compose stack not measured |
| NFR-PERF-02 | Met on this machine (worst case 0.29 s; target 3 s). Compose stack not measured |
| NFR-ACC-01 | Met on 14 screens by custom audit and a keyboard-only journey; observations recorded; no screen-reader test |
| RSP-01 to RSP-04 | No horizontal overflow in 84 of 84 combinations. Partial against the spec text: filter row not behind a toggle on mobile (RSP-03), two card columns at 1024 px (RSP-01), checkout not three-panel (section 7.3) |
| NFR-BRW-01 | Chromium, Edge and Firefox all 31 of 31; Chrome not installed |
| SCR-04, SCR-05 | Met after two CSS fixes |
| SCR-07 | Partial (layout) |
| SCR-08, SCR-09 | Not met (illustrated background, layout) – owner decision |

## 9. Not verified / pending

- Measurements against the Docker Compose stack (nginx frontend on `:3000`, backend and PostgreSQL containers): Docker and Podman are not installed on this machine. To be re-run with T141.
- E2E run against the Compose frontend on `:3000` (T135).
- Google Chrome itself (Edge and Chromium used as proxy).
- axe-core or a manual screen-reader audit.
- Load behaviour under concurrent users (not required by the spec).
- Card and UPI `placeOrder` timing (Cash on Delivery was timed).
- Firefox and WebKit were not re-run after the two CSS edits.
- Illustrated backgrounds for SCR-08 and SCR-09, the three-panel checkout and the SCR-08 method-list layout: awaiting the owner's decision.
