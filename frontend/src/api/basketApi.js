import apiClient from './axiosClient.js';

/** Calls for the basket operations of `api/openapi.yaml`. Every call resolves to a `Basket` unless noted. */

/** `previewBasket` (public): prices guest lines `[{bookId, quantity}]`; nothing is stored. */
export async function previewBasket(items) {
  const response = await apiClient.post('/basket/preview', { items });
  return response.data;
}

/** `getBasket`: the signed-in customer's basket. */
export async function getBasket() {
  const response = await apiClient.get('/basket');
  return response.data;
}

/** `addBasketItem`: adds `quantity` units (an existing line grows). Rejects with QUANTITY_LIMIT or OUT_OF_STOCK. */
export async function addBasketItem(bookId, quantity = 1) {
  const response = await apiClient.post('/basket/items', { bookId, quantity });
  return response.data;
}

/** `updateBasketItem`: sets the quantity (1..10) of a line. */
export async function updateBasketItem(bookId, quantity) {
  const response = await apiClient.put(`/basket/items/${bookId}`, { quantity });
  return response.data;
}

/** `removeBasketItem`: removes a line; resolves to an empty basket after the last removal. */
export async function removeBasketItem(bookId) {
  const response = await apiClient.delete(`/basket/items/${bookId}`);
  return response.data;
}

/** `mergeGuestBasket`: resolves to `{ basket, report: { adjustments } }`. */
export async function mergeGuestBasket(items) {
  const response = await apiClient.post('/basket/merge', { items });
  return response.data;
}

/** `getBasketRecommendations`: listed books in the categories of earlier orders, without basket books (a `BookSummary[]`). */
export async function getRecommendations(signal) {
  const response = await apiClient.get('/recommendations', { signal });
  return response.data;
}
