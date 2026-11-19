import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../api/axiosClient.js', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import apiClient from '../api/axiosClient.js';
import App from '../App.jsx';
import { bookDetail, bookPage, brands, categories, filterOptions } from '../test/fixtures/index.js';

const customer = { id: 1, fullName: 'Asha Verma', email: 'asha.verma@example.com' };
const emptyBasket = { items: [], itemCount: 0, subtotal: '0.00', tax: '0.00', deliveryCharge: '0.00', total: '0.00' };
const unauthenticated = { status: 401, code: 'UNAUTHENTICATED', message: 'Log in', fieldErrors: [] };

function LocationProbe() {
  const location = useLocation();
  return <p data-testid="location">{`${location.pathname}${location.search}`}</p>;
}

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
      <LocationProbe />
    </MemoryRouter>,
  );
}

function signedIn(isSignedIn) {
  apiClient.get.mockImplementation((url) => {
    if (url === '/auth/me') return isSignedIn ? Promise.resolve({ data: customer }) : Promise.reject(unauthenticated);
    if (url === '/categories') return Promise.resolve({ data: categories });
    if (url === '/brands') return Promise.resolve({ data: brands });
    if (url === '/books/filter-options') return Promise.resolve({ data: filterOptions });
    if (url === '/books') return Promise.resolve({ data: bookPage });
    if (url === '/books/7') return Promise.resolve({ data: bookDetail });
    if (url === '/books/7/related') return Promise.resolve({ data: [] });
    if (url === '/books/7/reviews') return Promise.resolve({ data: { averageRating: null, reviewCount: 0, reviews: [] } });
    if (url === '/rewards/me') return Promise.resolve({ data: { balance: 1000 } });
    if (url === '/addresses') return Promise.resolve({ data: [] });
    if (url === '/orders') return Promise.resolve({ data: [] });
    if (url === '/recommendations') return Promise.resolve({ data: [] });
    return Promise.resolve({ data: emptyBasket });
  });
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('public routes', () => {
  it.each([
    ['/', 'Catalogue'],
    ['/books/7', 'The Habit Loop'],
    ['/basket', 'Basket'],
    ['/wishlist', 'My Wishlist'],
    ['/writers', 'My Writers'],
    ['/login', 'Log in'],
    ['/register', 'Register'],
  ])('%s renders without logging in', async (path, heading) => {
    signedIn(false);
    renderAt(path);
    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent(path);
  });
});

describe('protected routes', () => {
  it.each([
    ['/orders', '/login?returnTo=%2Forders'],
    ['/orders/5', '/login?returnTo=%2Forders%2F5'],
    ['/checkout', '/login?returnTo=%2Fcheckout'],
    ['/payment', '/login?returnTo=%2Fpayment'],
    ['/account/addresses', '/login?returnTo=%2Faccount%2Faddresses'],
    ['/profile', '/login?returnTo=%2Fprofile'],
    ['/payment/confirmed/3', '/login?returnTo=%2Fpayment%2Fconfirmed%2F3'],
    ['/purchase/confirmed/3', '/login?returnTo=%2Fpurchase%2Fconfirmed%2F3'],
  ])('%s sends a guest to %s and shows no customer page', async (path, target) => {
    signedIn(false);
    renderAt(path);
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(target));
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'My Orders' })).not.toBeInTheDocument();
  });

  it('keeps the query string in returnTo', async () => {
    signedIn(false);
    renderAt('/orders?x=1');
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login?returnTo=%2Forders%3Fx%3D1'));
  });

  it('shows only a spinner while the session is being restored', () => {
    apiClient.get.mockReturnValue(new Promise(() => {}));
    renderAt('/orders');
    expect(screen.getByRole('status')).toHaveTextContent('Checking your session');
    expect(screen.queryByRole('heading', { name: 'My Orders' })).not.toBeInTheDocument();
  });

  it('opens the page for a signed-in customer', async () => {
    signedIn(true);
    renderAt('/orders');
    expect(await screen.findByRole('heading', { name: 'My Orders' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/orders');
  });
});

describe('profile', () => {
  it('opens My Profile for a signed-in customer with their details and gift points', async () => {
    signedIn(true);
    renderAt('/profile');
    expect(await screen.findByRole('heading', { name: 'My Profile' })).toBeInTheDocument();
    expect(await screen.findByText('asha.verma@example.com')).toBeInTheDocument();
    expect(await screen.findByText('1000 pts')).toBeInTheDocument();
    expect(await screen.findByText('No saved addresses')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Log out' })).toHaveLength(2);
  });
});

describe('not found', () => {
  it('shows the not-found page with a way back for an unknown route', async () => {
    signedIn(false);
    renderAt('/no/such/page');
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to the catalogue' })).toHaveAttribute('href', '/');
  });
});
