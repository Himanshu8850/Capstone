# Manual API test report (T145)

Run: 2026-11-17, local stack (PostgreSQL 18 on 127.0.0.1:5434, Spring Boot jar on 127.0.0.1:8080, seeded dev data). Compose is not used (owner-accepted exception).
Tool: [`docs/api-manual-tests/api-manual-test.cjs`](api-manual-tests/api-manual-test.cjs) (Node, no dependency). Command: `node docs/api-manual-tests/api-manual-test.cjs`.
It calls every one of the 29 operations in `api/openapi.yaml` once with success and at least one documented error path, checks status codes and the `ApiError` shape (`status`, `code`, `message`, `timestamp`, `fieldErrors` for `VALIDATION_FAILED`), and also covers: missing `BG_AUTH` cookie (401 `UNAUTHENTICATED`), missing CSRF token (rejected), another customer's address and order (404), Idempotency-Key replay, declined card `4000-0000-0000-0002` (`PAYMENT_DECLINED`), points above the cap, and a replayed cookie after `logout` (401). Only throw-away dummy customers are used; no token or cookie value is printed or stored.

Persisted effects were confirmed through the API reads in the same run (address created, updated and deleted; basket item added, changed and removed; order placed, cancelled; reward balance read). No database client (psql) is installed here, so no direct SQL read-only queries were made.

Defects found in the implementation: none. One test-script expectation was corrected (the points-above-cap case first sent a wrong `expectedPayableTotal` and correctly got `BASKET_CHANGED`).

## Result
| # | Operation | Case | Request | Expected | Actual | Note | Result |
|---|---|---|---|---|---|---|---|
| 1 | listStates | success | GET /reference/states | 200 | 200 |  | PASS |
| 2 | listCategories | success | GET /categories | 200 | 200 |  | PASS |
| 3 | listBrands | success | GET /brands | 200 | 200 |  | PASS |
| 4 | listBooks | success | GET /books | 200 | 200 |  | PASS |
| 5 | listBooks | invalid sort | GET /books | 400 VALIDATION_FAILED | 400 | VALIDATION_FAILED | PASS |
| 6 | getBookFilterOptions | success | GET /books/filter-options | 200 | 200 |  | PASS |
| 7 | getBook | success | GET /books/1 | 200 | 200 |  | PASS |
| 8 | getBook | unknown book | GET /books/999999 | 404 BOOK_NOT_FOUND | 404 | BOOK_NOT_FOUND | PASS |
| 9 | listRelatedBooks | success | GET /books/1/related | 200 | 200 |  | PASS |
| 10 | listRelatedBooks | unknown book | GET /books/999999/related | 404 BOOK_NOT_FOUND | 404 | BOOK_NOT_FOUND | PASS |
| 11 | previewBasket | success | POST /basket/preview | 200 | 200 |  | PASS |
| 12 | previewBasket | quantity 0 | POST /basket/preview | 400 VALIDATION_FAILED | 400 | VALIDATION_FAILED | PASS |
| 13 | register | success | POST /auth/register | 201 | 201 |  | PASS |
| 14 | register | duplicate e-mail | POST /auth/register | 409 EMAIL_TAKEN | 409 | EMAIL_TAKEN | PASS |
| 15 | register | password mismatch | POST /auth/register | 400 VALIDATION_FAILED | 400 | VALIDATION_FAILED | PASS |
| 16 | login | wrong password | POST /auth/login | 401 INVALID_CREDENTIALS | 401 | INVALID_CREDENTIALS | PASS |
| 17 | login | success | POST /auth/login | 200 | 200 |  | PASS |
| 18 | getCurrentCustomer | success | GET /auth/me | 200 | 200 |  | PASS |
| 19 | getCurrentCustomer | no cookie | GET /auth/me | 401 UNAUTHENTICATED | 401 | UNAUTHENTICATED | PASS |
| 20 | listAddresses | success | GET /addresses | 200 | 200 |  | PASS |
| 21 | listAddresses | no cookie | GET /addresses | 401 UNAUTHENTICATED | 401 | UNAUTHENTICATED | PASS |
| 22 | createAddress | invalid phone | POST /addresses | 400 VALIDATION_FAILED | 400 | VALIDATION_FAILED | PASS |
| 23 | createAddress | no CSRF token | POST /addresses | 401/403 | 401 | UNAUTHENTICATED | PASS |
| 24 | createAddress | success | POST /addresses | 201 | 201 |  | PASS |
| 25 | updateAddress | success | PUT /addresses/5 | 200 | 200 |  | PASS |
| 26 | updateAddress | another customer's address | PUT /addresses/5 | 404 NOT_FOUND | 404 | NOT_FOUND | PASS |
| 27 | getBasket | success | GET /basket | 200 | 200 |  | PASS |
| 28 | getBasket | no cookie | GET /basket | 401 UNAUTHENTICATED | 401 | UNAUTHENTICATED | PASS |
| 29 | addBasketItem | success | POST /basket/items | 200/201 | 200 |  | PASS |
| 30 | addBasketItem | unknown book | POST /basket/items | 404 BOOK_NOT_FOUND | 404 | BOOK_NOT_FOUND | PASS |
| 31 | mergeGuestBasket | success | POST /basket/merge | 200 | 200 |  | PASS |
| 32 | mergeGuestBasket | quantity 0 | POST /basket/merge | 400 VALIDATION_FAILED | 400 | VALIDATION_FAILED | PASS |
| 33 | updateBasketItem | success | PUT /basket/items/13 | 200 | 200 |  | PASS |
| 34 | updateBasketItem | item not in basket | PUT /basket/items/14 | 404 NOT_FOUND | 404 | NOT_FOUND | PASS |
| 35 | removeBasketItem | success | DELETE /basket/items/13 | 200 | 200 |  | PASS |
| 36 | removeBasketItem | item not in basket | DELETE /basket/items/13 | 404 NOT_FOUND | 404 | NOT_FOUND | PASS |
| 37 | getBasketRecommendations | success | GET /recommendations | 200 | 200 |  | PASS |
| 38 | getBasketRecommendations | no cookie | GET /recommendations | 401 UNAUTHENTICATED | 401 | UNAUTHENTICATED | PASS |
| 39 | getRewardAccount | success | GET /rewards/me | 200 | 200 |  | PASS |
| 40 | getRewardAccount | no cookie | GET /rewards/me | 401 UNAUTHENTICATED | 401 | UNAUTHENTICATED | PASS |
| 41 | quoteCheckout | negative points | POST /checkout/quote | 400 VALIDATION_FAILED | 400 | VALIDATION_FAILED | PASS |
| 42 | quoteCheckout | success | POST /checkout/quote | 200 | 200 |  | PASS |
| 43 | placeOrder | declined card (4000-0000-0000-0002) | POST /orders | 409 PAYMENT_DECLINED | 409 | PAYMENT_DECLINED | PASS |
| 44 | placeOrder | points above the cap | POST /orders | 409/400 POINTS_LIMIT_EXCEEDED | 409 | POINTS_LIMIT_EXCEEDED | PASS |
| 45 | placeOrder | success (cash on delivery) | POST /orders | 201 | 201 |  | PASS |
| 46 | placeOrder | replay with the same Idempotency-Key | POST /orders | 200/201 | 200 |  | PASS |
| 47 | listOrders | success | GET /orders | 200 | 200 |  | PASS |
| 48 | listOrders | no cookie | GET /orders | 401 UNAUTHENTICATED | 401 | UNAUTHENTICATED | PASS |
| 49 | getOrder | success | GET /orders/112 | 200 | 200 |  | PASS |
| 50 | getOrder | another customer's order | GET /orders/112 | 404 NOT_FOUND | 404 | NOT_FOUND | PASS |
| 51 | buyAgain | success | POST /orders/112/buy-again | 200 | 200 |  | PASS |
| 52 | buyAgain | another customer's order | POST /orders/112/buy-again | 404 NOT_FOUND | 404 | NOT_FOUND | PASS |
| 53 | cancelOrder | another customer's order | POST /orders/112/cancellation | 404 NOT_FOUND | 404 | NOT_FOUND | PASS |
| 54 | cancelOrder | success | POST /orders/112/cancellation | 200 | 200 |  | PASS |
| 55 | cancelOrder | already cancelled | POST /orders/112/cancellation | 409 ALREADY_CANCELLED | 409 | ALREADY_CANCELLED | PASS |
| 56 | deleteAddress | another customer's address | DELETE /addresses/5 | 404 NOT_FOUND | 404 | NOT_FOUND | PASS |
| 57 | deleteAddress | success | DELETE /addresses/5 | 204 | 204 |  | PASS |
| 58 | logout | no cookie | POST /auth/logout | 401 UNAUTHENTICATED | 401 | UNAUTHENTICATED | PASS |
| 59 | logout | success | POST /auth/logout | 204 | 204 |  | PASS |
| 60 | getCurrentCustomer | replayed cookie after logout | GET /auth/me | 401 UNAUTHENTICATED | 401 | UNAUTHENTICATED | PASS |

Operations covered: 29 of 29. Cases: 60. Passed: 60. Failed: 0.

