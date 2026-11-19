# API Contract Strategy & Endpoint Outline: BookyGeek

**Feature**: `001-bookygeek-platform` | **Date**: 2026-11-12 | **Plan**: [../plan.md](../plan.md)

> **This is NOT `openapi.yaml`.** It is the agreed *outline* that the first implementation tasks turn into the
> contract. **Gate G-API** (constitution Principle IV, NFR-API-04, PL-10): `api/openapi.yaml` must be authored,
> validated in Swagger Editor and **approved** before any controller is implemented. No endpoint is implemented
> in the planning phase.

## 1. Strategy

| Topic | Decision |
|---|---|
| Source of truth | Hand-written `api/openapi.yaml` (OpenAPI 3.0.3 for broad Swagger Editor support; 3.1 only if the editor validates it cleanly), kept in the repository root `api/` folder. Backend code is written to match it; **no springdoc / code-generated spec** (OD-10). |
| Validation | Paste/load into Swagger Editor (hosted, or a local Swagger Editor container run only as a developer tool, not part of the product Compose file). Validation screenshot/log attached to the approval record. |
| Versioning | URL prefix `/api/v1`. Changes after approval follow Principle IX (revision note in the spec/plan, spec change in the same PR as the code). |
| Resource style | Plural nouns, JSON only (`application/json`), camelCase fields, ISO-8601 dates/instants, money as **decimal strings** (`"1090.00"`), enums as upper-case strings, ids as integers. |
| Status codes | 200 read/update/action, 201 create (+`Location`), 204 delete/logout, 400 invalid input, 401 unauthenticated, 404 unknown or not-owned resource, 409 business conflict, 500 generic. 403 is not used (single role; foreign data answers 404). |
| One error schema | `ApiError { status:int, code:string, message:string, fieldErrors:[{field,message}]?, timestamp }`. Every 4xx/5xx references it. Machine-readable `code` values listed in §4. |
| Pagination | `page` (0-based), `size` fixed server-side at **12** (`size` param not exposed — FR-CAT-10). Response: `{ items[], page, pageSize, totalItems, totalPages }`. |
| Security scheme | Cookie auth `BG_AUTH` (HttpOnly) + `X-XSRF-TOKEN` header on state-changing calls. Declared as `securitySchemes` (`apiKey in: cookie`) plus a documented `csrfHeader` parameter. Public operations carry `security: []`. |
| Validation | Bean Validation annotations on request DTOs mirror schema constraints (`minLength`, `pattern`, `minimum`, `maximum`); the contract is authoritative. |
| Contract tests | Every operation has `operationId`; the repo contains `api/TRACEABILITY.md` mapping operationId → controller method → test class/method. Tests assert status + body shape (JSON paths) incl. error shape. |
| Examples | Each operation includes at least one success and one error example that reuse the documented dummy data (PL-05/PL-06). |

## 2. Endpoint outline (base `/api/v1`)

Auth column: **P** public, **C** customer (valid JWT cookie). Modules per spec §2.12.

### Auth & customer — module User & Authentication
| operationId | Method & path | Auth | Success | Key errors | Requirements |
|---|---|---|---|---|---|
| register | `POST /auth/register` | P | 201 `Customer` | 400 validation; 409 `EMAIL_TAKEN` | FR-AUTH-01/02 |
| login | `POST /auth/login` | P | 200 `Customer` + sets cookies | 401 `INVALID_CREDENTIALS` (generic) | FR-AUTH-03/04 |
| logout | `POST /auth/logout` | C | 204 + expires cookie, bumps token version | 401 | FR-AUTH-05 |
| getCurrentCustomer | `GET /auth/me` | C | 200 `Customer` (`id, fullName, email`) | 401 | FR-AUTH-03 |
| listAddresses | `GET /addresses` | C | 200 `Address[]` | 401 | FR-CHK-03 |
| createAddress | `POST /addresses` | C | 201 `Address` | 400 | FR-CHK-02/04 |
| updateAddress | `PUT /addresses/{id}` | C | 200 `Address` | 400, 404 | FR-CHK-03 |
| deleteAddress | `DELETE /addresses/{id}` | C | 204 | 404 | FR-CHK-03 |
| listStates | `GET /reference/states` | P | 200 `string[]` | — | FR-CHK-02 |

### Catalogue, categories, brands — modules Book Catalogue, Category & Brand
| operationId | Method & path | Auth | Success | Key errors | Requirements |
|---|---|---|---|---|---|
| listCategories | `GET /categories` | P | 200 tree `Category{id,name,children[]}` | — | FR-CAT-01 |
| listBrands | `GET /brands` | P | 200 `Brand{name,bookCount}[]` | — | FR-CAT-02 |
| listBooks | `GET /books?category&brand&q&author&language&format&minPrice&maxPrice&sort&page` | P | 200 `BookPage` | 400 bad param | FR-CAT-01..10 |
| getBookFilterOptions | `GET /books/filter-options` | P | 200 `{languages[],formats[],priceBands[]}` | — | FR-CAT-05 |
| getBook | `GET /books/{id}` | P | 200 `BookDetail` (adds `publisher, language, breadcrumb[], backCoverUrl`) | 404 `BOOK_NOT_FOUND` | FR-DET-01/02/06/09 |
| listRelatedBooks | `GET /books/{id}/related` | P | 200 `BookSummary[]` | 404 | FR-DET-04/05 |

`sort` ∈ `RELEVANCE`(default) \| `PRICE_ASC` \| `PRICE_DESC` \| `TITLE_ASC`. `category` filters the whole
subtree. `BookSummary` = `{id,title,author,description,format,publisher,language,price,frontCoverUrl,
categories[{id,name}],available,deliveryDate}`.

### Basket — module Shopping Cart
| operationId | Method & path | Auth | Success | Key errors | Requirements |
|---|---|---|---|---|---|
| previewBasket | `POST /basket/preview` | P | 200 `Basket` (priced guest lines + totals, no persistence) | 400 | FR-BSK-02/05 (guest) |
| getBasket | `GET /basket` | C | 200 `Basket` | 401 | FR-BSK-02/05/09 |
| addBasketItem | `POST /basket/items` `{bookId, quantity=1}` | C | 200 `Basket` | 404, 409 `QUANTITY_LIMIT`, `OUT_OF_STOCK` | FR-BSK-01 |
| updateBasketItem | `PUT /basket/items/{bookId}` `{quantity}` | C | 200 `Basket` | 404, 409 | FR-BSK-03 |
| removeBasketItem | `DELETE /basket/items/{bookId}` | C | 200 `Basket` | 404 | FR-BSK-04 |
| mergeGuestBasket | `POST /basket/merge` `{items:[{bookId,quantity}]}` | C | 200 `{basket, report}` | 400 | PL-01, FR-AUTH-08 |
| getBasketRecommendations | `GET /recommendations` | C | 200 `BookSummary[]` (empty when no history) | 401 | FR-BSK-07, FR-CAT-09, FR-LAND-03 |

`Basket` = `{items[{book:BookSummary, quantity, lineTotal}], itemCount, subtotal, tax, deliveryCharge, total}`.
Guest recommendations: the client does not call the endpoint (guests see none).

### Checkout, orders — modules Order, Payment, Reward Points
| operationId | Method & path | Auth | Success | Key errors | Requirements |
|---|---|---|---|---|---|
| getRewardAccount | `GET /rewards/me` | C | 200 `{balance}` | 401 | FR-RWD-01/05 |
| quoteCheckout | `POST /checkout/quote` `{pointsToRedeem}` | C | 200 `{basket totals, balance, maxRedeemablePoints, pointsToRedeem, payableTotal, issues[]}` | 409 `POINTS_LIMIT_EXCEEDED`, 400 | FR-CHK-05/07, FR-BSK-08, FR-PAY-09 |
| placeOrder | `POST /orders` (header `Idempotency-Key`) `{addressId \| address, paymentMethod, payment:{card\|upi}, pointsToRedeem, expectedPayableTotal}` | C | 201 `Order` | 400; 409 `PAYMENT_DECLINED`, `BASKET_CHANGED`, `OUT_OF_STOCK`, `POINTS_LIMIT_EXCEEDED`, `EMPTY_BASKET` | FR-CHK-08, FR-PAY-01..13, FR-RWD-02..04, FR-ORD-08 |
| listOrders | `GET /orders` | C | 200 `OrderSummary[]` newest first | 401 | FR-ORD-01 |
| getOrder | `GET /orders/{id}` | C | 200 `Order` (items, address, payment method/status, amounts, `cancellable`, `cancellableUntil`) | 404 | FR-ORD-02, FR-CNF-01/05 |
| cancelOrder | `POST /orders/{id}/cancellation` | C | 200 `Order` (CANCELLED) | 404; 409 `CANCELLATION_WINDOW_EXPIRED`, `ALREADY_CANCELLED` | FR-ORD-04..06 |
| buyAgain | `POST /orders/{id}/buy-again` | C | 200 `{basket, report}` | 404 | FR-ORD-03 |

Payment is a sub-step of `placeOrder`: Payment module is invoked in-process inside the order transaction; there is no
separate "pay" endpoint (this makes payment + order + points + stock atomic, NFR-DAT-02). `payment.card` =
`{number, nameOnCard, cvv, expiry}` accepted **only** in this request body, never echoed, persisted or logged.

`Order` = `{id, reference, status, placedAt, items[{book, quantity, unitPrice, lineTotal}], address,
paymentMethod, paymentStatus, subtotal, tax, deliveryCharge, pointsRedeemed, payableTotal, cancellable,
cancellableUntil}`.

## 3. Cross-cutting API rules

- Idempotent order placement: same `Idempotency-Key` + customer → same stored result (200/201 body replay), no
  second order (FR-PAY-11, FR-CNF-06).
- Ownership: every customer-scoped query includes `customer_id` from the JWT subject; never from client input.
- Input limits (lengths, patterns) are written into the schemas and mirrored by client validation.
- Responses never include `passwordHash`, `tokenVersion`, or card data. Entities are never returned (Principle II).
- CORS: no wildcard; allowlist from env, empty by default (same-origin via proxy).

## 4. Error codes (initial list for `ApiError.code`)

`VALIDATION_FAILED`, `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `EMAIL_TAKEN`, `NOT_FOUND`/`BOOK_NOT_FOUND`,
`QUANTITY_LIMIT`, `OUT_OF_STOCK`, `EMPTY_BASKET`, `BASKET_CHANGED`, `POINTS_LIMIT_EXCEEDED`, `PAYMENT_DECLINED`,
`CANCELLATION_WINDOW_EXPIRED`, `ALREADY_CANCELLED`, `INTERNAL_ERROR`.

## 5. Approval record (Gate G-API)


### Validation record (T021, 2026-11-13) — not an approval

`api/openapi.yaml` (OpenAPI 3.0.3, hand-written) was validated with command-line tools because Docker and the hosted
Swagger Editor were not available in the authoring environment. This record does **not** tick any checkbox below;
the owner's review and approval is task T022.

| Check | Tool / method | Result |
|---|---|---|
| Structural validation, every `$ref` resolves | `@redocly/cli` 2.78.0 `lint` (recommended ruleset, `api/redocly.yaml`) | Valid, 0 errors, 0 warnings |
| OpenAPI schema validation | `@apidevtools/swagger-cli` 4.0.4 `validate` | Valid |
| Operation count and ids | Script over the parsed file | 29 operations, 29 unique `operationId` values matching §2 |
| Examples | Script over the parsed file | Every request body has examples; every JSON response has examples |
| Error examples | Script over the parsed file | One example for each of the 15 `ApiError.code` values |

Rule overrides in `api/redocly.yaml`: `operation-4xx-response` is off for the four parameterless public reads
(`listStates`, `listCategories`, `listBrands`, `getBookFilterOptions`) because §2 defines no client-error case for them,
and `info-license` is off because no licence is declared. All other recommended rules are active.

**Owner action:** before ticking the checklist, also paste the file into the hosted Swagger Editor
(https://editor.swagger.io) and confirm zero errors and warnings there.

**Decisions taken while writing the contract that are not spelled out in §2 (for owner review):**
- `register` and `login` are public (`security: []`) and need no `X-XSRF-TOKEN`; every POST/PUT/DELETE of a signed-in
  customer needs it together with the `BG_AUTH` cookie.
- `placeOrder` returns 201 for a new order and 200 with the stored body for an `Idempotency-Key` replay (§3).
- `quoteCheckout` returns 200 with an `issues[]` list for price/stock changes, and 409 `POINTS_LIMIT_EXCEEDED` or
  `EMPTY_BASKET` for the blocking cases.
- Added fields: `stockQuantity` (nullable) on `BookSummary`; `PriceBand{label,minPrice,maxPrice}`; merge/Buy Again
  `report.adjustments[{bookId,title,reason,requestedQuantity,addedQuantity}]`; `OrderSummary` carries `items` and
  `payableTotal` for the My Orders list.
- Order `reference` is `BG-` + zero-padded id (data-model §2.9); `paymentStatus` uses the stored payment states
  (`PAID`, `PENDING_ON_DELIVERY`, `REFUNDED`, `CANCELLED`; `FAILED` never appears on an order).
- `listBooks` also accepts `author` and `brand` (FR-CAT-02/03, CL-25); genre tags use `category`.



### Owner approval (T022, 2026-11-14)

The project owner reviewed `api/openapi.yaml` and approved it on 2026-11-14, after three review corrections made to the
file only (payment-method conditional validation on `PlaceOrderRequest`/`PaymentDetails`, required `stockQuantity`
rules on `BookSummary`, and the pricing/reward formulas documented on `CheckoutQuote`). The approved file is the one
re-validated after those corrections: SHA-256 `B497A43F287249F8F75D5C9F9459BED3E0EEC533BAFA037DEEC9495B5D65E49A`;
Redocly 2.78.0 lint valid (0 errors, 0 warnings); swagger-cli 4.0.4 valid; 29 operations / 29 unique `operationId`;
226 local `` all resolve; all 76 error responses use `ApiError`; 134 examples conform to their schemas.
The hosted Swagger Editor log was not produced (not available in the authoring environment); the validation above used command-line tools only.

- [X] `openapi.yaml` validates without errors (command-line validators; hosted Swagger Editor log not produced)
- [X] Every outline operation above exists with request/response/error schemas and examples
- [X] `ApiError` is referenced by every error response
- [X] Reviewed against spec FR/NFR traceability
- [X] Approved by: Project owner Date: 2026-11-14 (recorded in plan Gate Register)
