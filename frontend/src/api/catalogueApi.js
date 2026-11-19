import apiClient from './axiosClient.js';

/** Calls for the catalogue operations of `api/openapi.yaml`: categories, brands, books, filter options, one book, related books. */

export async function listCategories(signal) {
  const response = await apiClient.get('/categories', { signal });
  return response.data;
}

export async function listBrands(signal) {
  const response = await apiClient.get('/brands', { signal });
  return response.data;
}

export async function getBookFilterOptions(signal) {
  const response = await apiClient.get('/books/filter-options', { signal });
  return response.data;
}

/** `params` holds only the filters that are set; empty values are never sent. */
export async function listBooks(params, signal) {
  const clean = Object.fromEntries(Object.entries(params).filter(([, value]) => value !== '' && value != null));
  const response = await apiClient.get('/books', { params: clean, signal });
  return response.data;
}

/** `getBook`: one listed book with its back cover and breadcrumb; an unknown id rejects with `BOOK_NOT_FOUND`. */
export async function getBook(id, signal) {
  const response = await apiClient.get(`/books/${encodeURIComponent(id)}`, { signal });
  return response.data;
}

/** `listRelatedBooks`: other books that share a category with this book (an array, empty when there are none). */
export async function listRelatedBooks(id, signal) {
  const response = await apiClient.get(`/books/${encodeURIComponent(id)}/related`, { signal });
  return response.data;
}

/** `listBookReviews`: `{ averageRating (one decimal, null when none), reviewCount, reviews }`, newest first. */
export async function listBookReviews(id, signal) {
  const response = await apiClient.get(`/books/${encodeURIComponent(id)}/reviews`, { signal });
  return response.data;
}

/** `createBookReview`: the signed-in customer's one review of a book; resolves to the created review. */
export async function createBookReview(id, { rating, comment }) {
  const body = comment ? { rating, comment } : { rating };
  const response = await apiClient.post(`/books/${encodeURIComponent(id)}/reviews`, body);
  return response.data;
}
