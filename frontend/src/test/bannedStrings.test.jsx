import { act, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../api/axiosClient.js', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import apiClient from '../api/axiosClient.js';
import App from '../App.jsx';
import {
  address,
  apiErrors,
  basket,
  bookDetail,
  bookPage,
  brands,
  categories,
  checkoutQuote,
  customer,
  emptyBasket,
  filterOptions,
  order,
  orderSummary,
} from './fixtures/index.js';

// Elements that are deliberately not built (spec CL-04, CL-05, CL-06, CL-09, FR-LAND-03, DS-12).
// Wishlist and My Writers are allowed: they are frontend-only (localStorage), owner-approved under AMD-002.
const BANNED = [
  /book\s*worm/i,
  /coupon/i,
  /wallet/i,
  /bestsellers/i,
  /new launches/i,
];

const ROUTES = [
  '/',
  '/books/7',
  '/basket',
  '/wishlist',
  '/writers',
  '/login',
  '/register',
  '/checkout',
  '/payment',
  '/payment/confirmed/1',
  '/purchase/confirmed/1',
  '/orders',
  '/orders/1',
  '/account/addresses',
  '/profile',
  '/no/such/page',
];

async function textAt(path, signedIn) {
  apiClient.get.mockImplementation((url) => {
    if (url === '/auth/me') return signedIn ? Promise.resolve({ data: customer }) : Promise.reject(apiErrors.UNAUTHENTICATED);
    if (url === '/categories') return Promise.resolve({ data: categories });
    if (url === '/brands') return Promise.resolve({ data: brands });
    if (url === '/books/filter-options') return Promise.resolve({ data: filterOptions });
    if (url === '/books') return Promise.resolve({ data: bookPage });
    if (url === '/books/7') return Promise.resolve({ data: bookDetail });
    if (url === '/books/7/related') return Promise.resolve({ data: [] });
    if (url === '/books/7/reviews') return Promise.resolve({ data: { averageRating: null, reviewCount: 0, reviews: [] } });
    if (url === '/addresses') return Promise.resolve({ data: [address] });
    if (url === '/reference/states') return Promise.resolve({ data: ['Karnataka', 'Kerala'] });
    if (url === '/orders/1') return Promise.resolve({ data: order });
    if (url === '/orders') return Promise.resolve({ data: [orderSummary] });
    if (url === '/recommendations') return Promise.resolve({ data: [] });
    if (url === '/rewards/me') return Promise.resolve({ data: { balance: 1000 } });
    // /basket and /checkout are rendered with a line so their totals, summaries and buttons are checked too.
    const withLines = (path === '/basket' || path === '/checkout') && signedIn;
    return Promise.resolve({ data: withLines ? basket : emptyBasket });
  });
  apiClient.post.mockImplementation((url) =>
    url === '/checkout/quote' ? Promise.resolve({ data: checkoutQuote }) : Promise.resolve({ data: emptyBasket }),
  );
  const view = render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
  // Let the session check and the basket load settle before reading the page.
  await view.findByRole('banner');
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
  const text = document.body.textContent;
  const labels = Array.from(document.body.querySelectorAll('[aria-label],[alt],[title]'))
    .map((element) => [element.getAttribute('aria-label'), element.getAttribute('alt'), element.getAttribute('title')])
    .flat()
    .filter(Boolean)
    .join(' ');
  view.unmount();
  return `${text} ${labels}`;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('excluded features are never shown', () => {
  describe.each([
    ['a guest', false],
    ['a signed-in customer', true],
  ])('for %s', (_label, signedIn) => {
    it.each(ROUTES)('%s shows none of the banned strings', async (path) => {
      const text = await textAt(path, signedIn);
      for (const pattern of BANNED) {
        expect(text, `${path} must not match ${pattern}`).not.toMatch(pattern);
      }
    });
  });

  it('shows the brand as BookyGeek in the header', async () => {
    const text = await textAt('/', false);
    expect(text).toContain('BookyGeek');
  });
});
