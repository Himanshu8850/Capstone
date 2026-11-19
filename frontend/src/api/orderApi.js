import apiClient from './axiosClient.js';

/** Calls for the checkout and order operations of `api/openapi.yaml`. */

/** `quoteCheckout`: prices the basket with `pointsToRedeem` applied; nothing is stored. Resolves to a `CheckoutQuote`. */
export async function quoteCheckout(pointsToRedeem = 0) {
  const response = await apiClient.post('/checkout/quote', { pointsToRedeem });
  return response.data;
}

/**
 * `placeOrder`: creates the order and runs the simulated payment in one request. `idempotencyKey` is a UUID: sending
 * the same key again returns the same order, so a lost response can be retried without a second order.
 * Resolves to the `Order`; rejects with PAYMENT_DECLINED, BASKET_CHANGED, OUT_OF_STOCK or another ApiError.
 */
export async function placeOrder(request, idempotencyKey) {
  const response = await apiClient.post('/orders', request, { headers: { 'Idempotency-Key': idempotencyKey } });
  return response.data;
}

/** `getOrder`: one of the signed-in customer's orders. */
export async function getOrder(id, signal) {
  const response = await apiClient.get(`/orders/${id}`, { signal });
  return response.data;
}

/** `listOrders`: the signed-in customer's orders, newest first (an `OrderSummary[]`, empty when there are none). */
export async function listOrders(signal) {
  const response = await apiClient.get('/orders', { signal });
  return response.data;
}

/**
 * `cancelOrder`: cancels a `PLACED` order that is strictly under 48 hours old. Resolves to the cancelled `Order`;
 * rejects with CANCELLATION_WINDOW_EXPIRED or ALREADY_CANCELLED (409) with nothing changed.
 */
export async function cancelOrder(id) {
  const response = await apiClient.post(`/orders/${id}/cancellation`);
  return response.data;
}

/** `buyAgain`: adds the books of an earlier order to the basket. Resolves to `{ basket, report: { adjustments } }`. */
export async function buyAgain(id) {
  const response = await apiClient.post(`/orders/${id}/buy-again`);
  return response.data;
}
