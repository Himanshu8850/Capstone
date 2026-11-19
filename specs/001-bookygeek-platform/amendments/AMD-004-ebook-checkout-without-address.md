# Amendment AMD-004: No delivery address for an e-book only checkout

**Status: requested by the project owner, 2026-11-18.** Extends AMD-003; no new endpoint (31 operations). OpenAPI 1.3.0.

## What changed

- **Checkout** (`/checkout`): when every book in the basket is an e-book, nothing is delivered, so the delivery address form and the saved-address picker are not shown and no address is validated. A short note says that e-books are delivered digitally. A basket with at least one physical book behaves as before.
- **Payment** (`/payment`): the address is no longer required in the checkout state; it is sent only when checkout collected one.
- **placeOrder**: `addressId` and `address` are both optional. Sending both is still `VALIDATION_FAILED`. Sending neither is accepted only when the basket holds only e-books; for a basket with a physical book it is `VALIDATION_FAILED`. For an e-book only basket an address that is sent is ignored and not stored.
- **Order**: `address` is nullable (absent from `required`) and is `null` for an e-book only order. "Delivery address" is not shown on the purchase confirmation or the order detail page for such an order.
- **Data model**: Flyway `V4__ebook_order_without_address.sql` drops `NOT NULL` from the `orders.ship_*` columns (the PIN and phone format CHECKs still apply when a value is present).

## Artifacts affected

- `api/openapi.yaml` (1.3.0): `PlaceOrderRequest`, `Order.address`.
- `data-model.md`: `orders` address snapshot is optional.
- `tasks.md`: T165.
- Code: `OrderService`, `OrderEntity`, `CheckoutPage`, `PaymentPage`, `PurchaseConfirmedPage`, `OrderDetailPage`; tests in `OrderServiceTest`, `PlaceOrderIT`, `CheckoutPage.test.jsx`, `PaymentPage.test.jsx`, `e2e-05`.
