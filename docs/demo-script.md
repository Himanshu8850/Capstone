# BookyGeek – demo script (T142)

Use this script to demonstrate the verified local set-up. Every value below is dummy data. Do not show `.env`, environment variables, cookies or terminal history during the demo.

## 0. Before the demo

- Start the stack as described in `README.md` (Quick start with Docker Compose, or "running the parts without Compose"). The frontend is on `http://127.0.0.1:3000` with Compose, or `http://127.0.0.1:5173` with `npm run dev`.
- Use the seeded database (`dev` profile): 60 books, 9 of them eBooks, 19 leaf categories. There are **no pre-made customer accounts**.
- Use a fresh browser profile or private window, so no earlier basket or session is present.
- The demo covers the approved scope only. Wishlist, wallet, coupons, reviews, ratings, author profiles, "Bestsellers", "New Launches" and a real payment gateway are not part of the product, so do not look for them.

## 1. Dummy values

| Purpose | Value |
|---------|-------|
| New account | e-mail `reader@example.test`, name `Test Reader`, password `Passw0rdOK` (8 to 64 characters, at least one letter and one digit). Starts with 1,000 reward points |
| Address | name `Test Reader`, phone `9876543210`, address line 1 `12 MG Road`, city `Bengaluru`, state `Karnataka`, PIN `560001`, country India |
| Card approved | `4111-1111-1111-1111`, any name, CVV `123`, expiry in the future (`12/2099`) |
| Card declined | `4000-0000-0000-0002` |
| UPI approved | `demo@upi` |
| UPI declined | `failure@upi` |
| Cash on delivery | no payment details |
| Invalid inputs (validation) | phone with 9 or 11 digits; PIN with 5 or 7 digits |

## 2. Money reference (rules in force)

- Tax is 5% of the subtotal, rounded half up. Delivery is ₹40 for physical and mixed baskets and ₹0 for an eBook-only basket.
- Redeemable points are at most `min(balance, floor(0.5 × (subtotal + tax + delivery)))`, and 1 point is ₹1. Points are never earned by buying.
- Worked example, one copy of book 1 ("The Habit Ladder", paperback, ₹499): subtotal 499.00, tax 24.95, delivery 40.00, total **563.95**, maximum redeemable **281 points**. Redeeming 281 points leaves ₹282.95 to pay. 282 points is rejected.
- Second example, one copy of "Calm Mind, Clear Day" (₹349): subtotal 349.00, tax 17.45, delivery 40.00, total 406.45, maximum 203 points.
- A basket line holds 1 to 10 units, and never more than the stock of a physical book.

## 3. Journey (about 12 to 15 minutes)

| # | Scene | Steps | What to point out |
|---|-------|-------|-------------------|
| 1 | Overview | Open the landing page as a guest | 12 books per page, category tree, brands, "Page 1 of 5", header with My Orders, basket and Log in |
| 2 | Browse | Choose Non-Fiction → Self Help, pick a brand, search by title or author, filter by Language, Format and Price, change Sort by | Breadcrumb-style navigation, filters combine, count of books updates, no excluded sections |
| 3 | Book details | Open "The Habit Ladder" | Front and back cover, author link, publisher, format, language, price, stock, delivery date (five days out for a physical book, today for an eBook), Related Reads in the same category |
| 4 | Guest basket | Add two books as a guest | Basket badge in the header |
| 5 | Register | Register `reader@example.test`; observe the guest basket merged into the account basket after login | Quantities are summed and capped; account menu shows the name; the account has 1,000 points |
| 6 | Basket | Change a quantity with the steppers, try an 11th unit, remove a line, look at Recommendations | Limits are enforced; totals update |
| 7 | Checkout | Press Proceed to Checkout, enter the address from section 1, try a 9-digit phone number first | Inline validation; saved addresses appear on later visits; order summary arithmetic; reward panel shows balance and maximum |
| 8 | Rewards | Enter the maximum, then one point more | Maximum accepted; one more is rejected; points are only deducted when the order is placed |
| 9 | Declined payment | Pay Now, choose Credit Card, use `4000-0000-0000-0002` | A decline message; **no order is created**; basket, stock and points are unchanged |
| 10 | Successful card payment | Retry with `4111-1111-1111-1111`, CVV `123`, expiry `12/2099` | Payment confirmation, then purchase confirmation with order reference, totals, address and payment status; basket is empty |
| 11 | Other methods | Repeat for Debit Card, UPI (`failure@upi`, then `demo@upi`) and Cash on Delivery | COD skips the payment confirmation and leaves the payment "pending on delivery" |
| 12 | My Orders | Open My Orders, then an order | Status, items, totals, address and payment; another customer's order id returns "not found" |
| 13 | Buy Again | Press Buy Again on an earlier order | Items return to the basket; an unavailable book is reported and an over-stock quantity is reduced |
| 14 | Cancel | Cancel a PLACED order inside 48 hours | Status becomes CANCELLED; stock and redeemed points are restored; a paid order becomes REFUNDED; a COD order's pending payment becomes CANCELLED; a second cancellation is rejected |
| 15 | Keyboard and responsive (optional) | Tab through the login form and checkout; narrow the window to 640 and 375 px | Visible focus, labelled fields, no horizontal scroll |

## 4. What to say about the build (for the recording, T154)

- Modular monolith with seven business modules (`user`, `reward`, `category`, `catalogue`, `cart`, `payment`, `order`) plus `common`; module boundaries are enforced by `ModuleBoundaryTest`.
- React and Vite frontend, Spring Boot 4.x backend, PostgreSQL 18, Docker Compose; the hand-written `api/openapi.yaml` is the contract and every contract operation is traced to a test in `api/TRACEABILITY.md`.
- Specification, plan and tasks drove the build, with approval gates for the contract and the data model.
- Test evidence: `docs/test-matrix.md` (requirements to tests), `docs/nfr-verification.md` (performance, accessibility, responsive, browsers, wireframes) and `docs/security-scan-results.md`.

## 5. Known limits to be honest about during the demo

- Illustrated backgrounds on the payment and purchase-confirmation screens are not implemented (`docs/nfr-verification.md` section 7.3).
- The Docker Compose stack and `quickstart.md` have not yet been walked on a machine with Docker (T141, T135).
- Payments are simulated; no card or UPI detail is stored or logged.
