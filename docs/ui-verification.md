# UI verification (T147)

Date: 2026-11-17. Built from evidence already recorded; no screen needed a new walk. Sources: [`nfr-verification.md`](nfr-verification.md) (T139: 14 screens at 1280, 1024, 640 and 375 px, 56 screenshots in [`screenshots/`](screenshots/), 0 of 84 viewport and screen combinations overflow, keyboard-only journey 10 of 10), the Playwright E2E-01..08 scenarios (31 tests, Chromium) and the Vitest component tests. Stack: local PostgreSQL, Spring Boot jar and Vite dev server (Compose is not used; owner-accepted exception).

| Screen | Evidence | Browser and widths | Result |
|---|---|---|---|
| SCR-01/04 Catalogue: category tree and highlight, brands, search, language/format/price filters, sort, paging | E2E-02; `screenshots/catalogue-*.jpg` | Chromium; 1280, 1024, 640, 375 | Pass |
| SCR-02 Login, logout, `returnTo` | E2E-01, E2E-04; `login-*.jpg` | Chromium; all four widths | Pass |
| SCR-03 Registration, validation messages, duplicate e-mail | E2E-01; `register-*.jpg` | Chromium; all four widths | Pass |
| SCR-05 Book details, Related Reads, Add to Basket, delivery date, unavailable and eBook cases | E2E-03; `book-detail-*.jpg` | Chromium; all four widths | Pass |
| SCR-06 Basket: guest, merge at login, steppers, remove, 11th unit rejected, totals, empty state, Recommended for You | E2E-04, E2E-07; `basket-*.jpg`, `basket-empty-*.jpg` | Chromium; all four widths | Pass |
| SCR-07 Checkout: saved addresses, new address, validation, reward redemption | E2E-05, E2E-08; `checkout-*.jpg`, `addresses-*.jpg` | Chromium; all four widths | Pass (layout deviation accepted, below) |
| SCR-08 Payment: credit card, debit card, UPI, cash on delivery; approved and declined values; retry after decline; a decline changes nothing | E2E-05, E2E-06; `payment-*.jpg` | Chromium; all four widths | Pass (background deviation accepted, below) |
| SCR-12 Payment confirmation | E2E-05; `payment-confirmed-*.jpg` | Chromium; all four widths | Pass |
| SCR-09 Purchase confirmation | E2E-05; `purchase-confirmed-*.jpg` | Chromium; all four widths | Pass (background deviation accepted, below) |
| SCR-10/11 My Orders, order detail, cancel within 48 hours, Buy Again, another customer's order not found | E2E-07; `orders-*.jpg`, `order-detail-*.jpg` | Chromium; all four widths | Pass |
| SCR-13 Empty, loading, error and not-found states | E2E-03, E2E-07, component tests; `not-found-*.jpg`, `basket-empty-*.jpg` | Chromium; all four widths | Pass |
| SCR-14 Excluded features absent (wishlist, My Writers, reviews, ratings, coupons, wallet, Bestsellers this Month, New Launches, Book Worm) | Banned-strings test; T143 search of the source | n/a | Confirmed absent |

## CL-25 navigation

| Check | Evidence | Result |
|---|---|---|
| Category tree lists the selected category's books (a parent includes its sub-categories) and highlights the selection | E2E-02 | Pass |
| Breadcrumb links open the catalogue at that level | E2E-03 | Pass |
| Genre-tag links filter the catalogue by that genre | E2E-03 (genre link visible), `BookCard` and `BookDetailPage` component tests (the tag links to the catalogue filtered by that genre category) | Pass |
| Author name opens the catalogue filtered by that author | E2E-03 (`?author=`), `BookCard` and `RecommendedForYou` component tests | Pass |

## Cross-cutting

- No horizontal overflow at 1280, 1024, 1023, 640, 639 and 375 px (0 of 84 combinations, T139). Keyboard-only journey 10 of 10; 0 unlabelled controls; minimum contrast 4.81:1.
- No broken link or dead end was found in the E2E walks. No defect was found in this task, so no code change was made for T147.
- Known limits from T139, unchanged: no axe-core or screen-reader test, no skip link, the not-found page has no `h1`.

## Accepted deviations (SC-003, owner-accepted)

1. No illustrated background on SCR-08 and SCR-09.
2. Checkout is two columns, not the wireframe's three panels.
3. The filter row is not behind a toggle on mobile (RSP-03).
4. Two card columns at 1024 px.

All other deviations from the wireframes are the approved ones in `spec.md` (CL-04, CL-05, CL-06, CL-09, CL-15, CL-17).
