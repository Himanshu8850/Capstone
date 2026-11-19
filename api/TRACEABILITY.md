# API traceability: `operationId` → controller → tests

Source of truth: [`openapi.yaml`](openapi.yaml) (gate G-API approved 2026-11-14; version 1.1.0 adds rows 30 and 31 under AMD-002, approved 2026-11-18; version 1.2.0 adds the optional `authorBio` field to `BookDetail` under AMD-003; version 1.3.0 makes the address optional for an e-book only order under AMD-004). 31 operations, base path `/api/v1`.
All 31 rows are **implemented**. T133 verified that the nine `@RestController` classes in `backend/src/main` exposed
exactly the first 29 mappings; rows 30 and 31 are served by `catalogue.controller.ReviewController` and are exercised by `integration.ReviewIT`.

Package prefix for controllers: `com.bookygeek`. Test classes live under `backend/src/test/java/com/bookygeek/`
(`contract/` = response validated against `openapi.yaml`; `integration/` = Spring Boot + PostgreSQL).

| # | Method and path | `operationId` | Controller | Contract test | Integration and unit tests | Task | Status |
|---|---|---|---|---|---|---|---|
| 1 | POST `/auth/register` | `register` | `user.controller.AuthController` | `contract.AuthContractTest` | `integration.AuthFlowIT` | T058 | implemented |
| 2 | POST `/auth/login` | `login` | `user.controller.AuthController` | `contract.AuthContractTest` | `integration.AuthFlowIT` | T058 | implemented |
| 3 | POST `/auth/logout` | `logout` | `user.controller.AuthController` | `contract.AuthContractTest` | `integration.AuthFlowIT` | T058 | implemented |
| 4 | GET `/auth/me` | `getCurrentCustomer` | `user.controller.AuthController` | `contract.AuthContractTest` | `integration.AuthFlowIT` | T058 | implemented |
| 5 | GET `/addresses` | `listAddresses` | `user.controller.AddressController` | `contract.AddressContractTest` | `integration.AddressIT` | T105 | implemented |
| 6 | POST `/addresses` | `createAddress` | `user.controller.AddressController` | `contract.AddressContractTest` | `integration.AddressIT` | T105 | implemented |
| 7 | PUT `/addresses/{id}` | `updateAddress` | `user.controller.AddressController` | `contract.AddressContractTest` | `integration.AddressIT` | T105 | implemented |
| 8 | DELETE `/addresses/{id}` | `deleteAddress` | `user.controller.AddressController` | `contract.AddressContractTest` | `integration.AddressIT` | T105 | implemented |
| 9 | GET `/reference/states` | `listStates` | `user.controller.AddressController` | `contract.AddressContractTest` | `integration.AddressIT` | T105 | implemented |
| 10 | GET `/categories` | `listCategories` | `category.controller.CategoryController` | `contract.CatalogueContractTest` | `integration.CatalogueQueryIT` | T068 | implemented |
| 11 | GET `/brands` | `listBrands` | `category.controller.CategoryController` | `contract.CatalogueContractTest` | `integration.CatalogueQueryIT` | T068 | implemented |
| 12 | GET `/books` | `listBooks` | `catalogue.controller.BookController` | `contract.CatalogueContractTest` | `integration.CatalogueQueryIT` | T071 | implemented |
| 13 | GET `/books/filter-options` | `getBookFilterOptions` | `catalogue.controller.BookController` | `contract.CatalogueContractTest` | `integration.CatalogueQueryIT` | T071 | implemented |
| 14 | GET `/books/{id}` | `getBook` | `catalogue.controller.BookController` | `contract.CatalogueContractTest` | `integration.BookDetailIT` | T080 | implemented |
| 15 | GET `/books/{id}/related` | `listRelatedBooks` | `catalogue.controller.BookController` | `contract.CatalogueContractTest` | `integration.BookDetailIT` | T080 | implemented |
| 16 | POST `/basket/preview` | `previewBasket` | `cart.controller.BasketController` | `contract.BasketContractTest` | `integration.BasketIT`, `unit.cart.CartServiceTest` | T092 | implemented |
| 17 | GET `/basket` | `getBasket` | `cart.controller.BasketController` | `contract.BasketContractTest` | `integration.BasketIT`, `unit.cart.CartServiceTest` | T092 | implemented |
| 18 | POST `/basket/items` | `addBasketItem` | `cart.controller.BasketController` | `contract.BasketContractTest` | `integration.BasketIT`, `unit.cart.CartServiceTest` | T092 | implemented |
| 19 | PUT `/basket/items/{bookId}` | `updateBasketItem` | `cart.controller.BasketController` | `contract.BasketContractTest` | `integration.BasketIT`, `unit.cart.CartServiceTest` | T092 | implemented |
| 20 | DELETE `/basket/items/{bookId}` | `removeBasketItem` | `cart.controller.BasketController` | `contract.BasketContractTest` | `integration.BasketIT`, `unit.cart.CartServiceTest` | T092 | implemented |
| 21 | POST `/basket/merge` | `mergeGuestBasket` | `cart.controller.BasketController` | `contract.BasketContractTest` | `integration.BasketIT`, `unit.cart.CartServiceTest` | T092 | implemented |
| 22 | GET `/recommendations` | `getBasketRecommendations` | `order.controller.RecommendationController` | `contract.OrderContractTest` | `integration.OrderHistoryIT`, `unit.order.RecommendationServiceTest` | T122 | implemented |
| 23 | GET `/rewards/me` | `getRewardAccount` | `reward.controller.RewardController` | `contract.RewardContractTest` | `integration.RewardRedemptionIT`, `unit.reward.RewardCapTest` | T130 | implemented |
| 24 | POST `/checkout/quote` | `quoteCheckout` | `order.controller.CheckoutController` | `contract.CheckoutContractTest` | `integration.PlaceOrderIT`, `integration.StockConcurrencyIT`, `unit.order.OrderServiceTest`, `unit.payment.PaymentServiceTest` | T110 | implemented |
| 25 | GET `/orders` | `listOrders` | `order.controller.OrderController` | `contract.OrderContractTest` | `integration.OrderHistoryIT` | T121 | implemented |
| 26 | POST `/orders` | `placeOrder` | `order.controller.CheckoutController` | `contract.CheckoutContractTest` | `integration.PlaceOrderIT`, `integration.StockConcurrencyIT`, `unit.order.OrderServiceTest`, `unit.payment.PaymentServiceTest` | T110 | implemented |
| 27 | GET `/orders/{id}` | `getOrder` | `order.controller.OrderController` | `contract.CheckoutContractTest`, `contract.OrderContractTest` | `integration.PlaceOrderIT`, `integration.OrderHistoryIT` | T110, T121 | implemented |
| 28 | POST `/orders/{id}/cancellation` | `cancelOrder` | `order.controller.OrderController` | `contract.OrderContractTest` | `integration.CancelOrderIT`, `unit.order.CancellationRulesTest` | T121 | implemented |
| 29 | POST `/orders/{id}/buy-again` | `buyAgain` | `order.controller.OrderController` | `contract.OrderContractTest` | `integration.OrderHistoryIT`, `unit.order.BuyAgainServiceTest` | T122 | implemented |
| 30 | GET `/books/{id}/reviews` | `listBookReviews` | `catalogue.controller.ReviewController` | `integration.ReviewIT` (status codes and error codes) | `integration.ReviewIT` | T158 | implemented |
| 31 | POST `/books/{id}/reviews` | `createBookReview` | `catalogue.controller.ReviewController` | `integration.ReviewIT` (status codes and error codes) | `integration.ReviewIT` | T158 | implemented |

## Rules

- Every controller method maps to exactly one row above; an endpoint not in this table must not be added.
- A change to a path, method, `operationId`, schema or error code requires an `openapi.yaml` change first and a new
  owner approval of the contract (gate G-API).
