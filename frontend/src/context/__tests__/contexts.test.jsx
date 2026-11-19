import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/axiosClient.js', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import apiClient from '../../api/axiosClient.js';
import { AuthProvider, useAuth } from '../AuthContext.jsx';
import { BasketProvider, EMPTY_BASKET, useBasketContext } from '../BasketContext.jsx';
import { readGuestBasket, writeGuestBasket } from '../../utils/storage.js';
import { PASSWORD } from '../../test/credentials';

const customer = { id: 1, fullName: 'Asha Verma', email: 'asha.verma@example.com' };

const basketOf = (quantity) => ({
  items: [{ book: { id: 7, title: 'The Habit Loop' }, quantity, lineTotal: '500.00' }],
  itemCount: quantity,
  subtotal: '500.00',
  tax: '25.00',
  deliveryCharge: '40.00',
  total: '565.00',
});

function Probe() {
  const auth = useAuth();
  const basket = useBasketContext();
  return (
    <div>
      <p data-testid="who">{auth.loading ? 'loading' : auth.customer ? auth.customer.fullName : 'guest'}</p>
      <p data-testid="count">{basket.loading ? 'loading' : basket.itemCount}</p>
      <p data-testid="merge">{basket.mergeReport ? basket.mergeReport.adjustments.length : 'none'}</p>
      <button onClick={() => auth.login('asha.verma@example.com', PASSWORD)}>login</button>
      <button onClick={() => auth.logout().catch(() => {})}>logout</button>
      <button onClick={() => basket.addItem(7, 1).catch((e) => (window.lastError = e))}>add</button>
      <button onClick={() => basket.updateItem(7, 3)}>update</button>
      <button onClick={() => basket.removeItem(7)}>remove</button>
    </div>
  );
}

function renderProbe() {
  return render(
    <AuthProvider>
      <BasketProvider>
        <Probe />
      </BasketProvider>
    </AuthProvider>,
  );
}

const unauthenticated = { status: 401, code: 'UNAUTHENTICATED', message: 'Log in', fieldErrors: [] };

beforeEach(() => {
  vi.clearAllMocks();
  window.lastError = undefined;
});

describe('AuthContext', () => {
  it('restores the session from getCurrentCustomer and keeps nothing in web storage', async () => {
    apiClient.get.mockImplementation((url) =>
      url === '/auth/me' ? Promise.resolve({ data: customer }) : Promise.resolve({ data: EMPTY_BASKET }),
    );
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('who')).toHaveTextContent('Asha Verma'));
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it('is a guest when the session check returns 401', async () => {
    apiClient.get.mockRejectedValue(unauthenticated);
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('who')).toHaveTextContent('guest'));
  });

  it('logs in and out through the API and clears the customer even if logout fails', async () => {
    apiClient.get.mockImplementation((url) =>
      url === '/auth/me' ? Promise.reject(unauthenticated) : Promise.resolve({ data: EMPTY_BASKET }),
    );
    apiClient.post.mockImplementation((url) => {
      if (url === '/auth/login') return Promise.resolve({ data: customer });
      if (url === '/auth/logout') return Promise.reject({ status: 500, code: 'INTERNAL_ERROR' });
      return Promise.resolve({ data: { basket: EMPTY_BASKET, report: { adjustments: [] } } });
    });
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('who')).toHaveTextContent('guest'));

    await userEvent.click(screen.getByText('login'));
    await waitFor(() => expect(screen.getByTestId('who')).toHaveTextContent('Asha Verma'));
    expect(apiClient.post).toHaveBeenCalledWith('/auth/login', {
      email: 'asha.verma@example.com',
      password: PASSWORD,
    });

    await userEvent.click(screen.getByText('logout'));
    await waitFor(() => expect(screen.getByTestId('who')).toHaveTextContent('guest'));
  });
});

describe('BasketContext as a guest', () => {
  beforeEach(() => {
    apiClient.get.mockRejectedValue(unauthenticated);
    apiClient.post.mockImplementation((url, body) =>
      url === '/basket/preview'
        ? Promise.resolve({ data: basketOf(body.items.reduce((n, l) => n + l.quantity, 0)) })
        : Promise.reject(new Error(`unexpected ${url}`)),
    );
  });

  it('shows an empty basket without calling the server when nothing is stored', async () => {
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('0'));
    expect(apiClient.post).not.toHaveBeenCalled();
  });

  it('prices stored lines through previewBasket on load', async () => {
    writeGuestBasket([{ bookId: 7, quantity: 2 }]);
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('2'));
    expect(apiClient.post).toHaveBeenCalledWith('/basket/preview', { items: [{ bookId: 7, quantity: 2 }] });
  });

  it('adds, updates and removes lines in localStorage as {bookId, quantity} only', async () => {
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('0'));

    await userEvent.click(screen.getByText('add'));
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('1'));
    expect(readGuestBasket()).toEqual([{ bookId: 7, quantity: 1 }]);

    await userEvent.click(screen.getByText('add'));
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('2'));
    expect(readGuestBasket()).toEqual([{ bookId: 7, quantity: 2 }]);

    await userEvent.click(screen.getByText('update'));
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('3'));
    expect(readGuestBasket()).toEqual([{ bookId: 7, quantity: 3 }]);

    await userEvent.click(screen.getByText('remove'));
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('0'));
    expect(readGuestBasket()).toEqual([]);
    expect(window.localStorage.getItem('bookygeek.guestBasket')).not.toContain('title');
  });

  it('rejects an 11th unit with QUANTITY_LIMIT and leaves storage unchanged', async () => {
    writeGuestBasket([{ bookId: 7, quantity: 10 }]);
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('10'));
    await userEvent.click(screen.getByText('add'));
    await waitFor(() => expect(window.lastError).toMatchObject({ code: 'QUANTITY_LIMIT' }));
    expect(readGuestBasket()).toEqual([{ bookId: 7, quantity: 10 }]);
  });
});

describe('BasketContext as a customer', () => {
  it('loads the server basket', async () => {
    apiClient.get.mockImplementation((url) =>
      url === '/auth/me' ? Promise.resolve({ data: customer }) : Promise.resolve({ data: basketOf(4) }),
    );
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('4'));
    expect(apiClient.get).toHaveBeenCalledWith('/basket');
  });

  it('uses the server for add, update and remove and never touches localStorage', async () => {
    apiClient.get.mockImplementation((url) =>
      url === '/auth/me' ? Promise.resolve({ data: customer }) : Promise.resolve({ data: basketOf(1) }),
    );
    apiClient.post.mockResolvedValue({ data: basketOf(2) });
    apiClient.put.mockResolvedValue({ data: basketOf(3) });
    apiClient.delete.mockResolvedValue({ data: EMPTY_BASKET });
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('1'));

    await userEvent.click(screen.getByText('add'));
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('2'));
    expect(apiClient.post).toHaveBeenCalledWith('/basket/items', { bookId: 7, quantity: 1 });

    await userEvent.click(screen.getByText('update'));
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('3'));
    expect(apiClient.put).toHaveBeenCalledWith('/basket/items/7', { quantity: 3 });

    await userEvent.click(screen.getByText('remove'));
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('0'));
    expect(apiClient.delete).toHaveBeenCalledWith('/basket/items/7');
    expect(window.localStorage.length).toBe(0);
  });

  it('merges the guest basket at login, clears it only after success, and surfaces the report', async () => {
    writeGuestBasket([
      { bookId: 7, quantity: 2 },
      { bookId: 31, quantity: 1 },
    ]);
    let signedIn = false;
    apiClient.get.mockImplementation((url) =>
      url === '/auth/me' ? Promise.reject(unauthenticated) : Promise.resolve({ data: basketOf(0) }),
    );
    apiClient.post.mockImplementation((url, body) => {
      if (url === '/auth/login') {
        signedIn = true;
        return Promise.resolve({ data: customer });
      }
      if (url === '/basket/preview') return Promise.resolve({ data: basketOf(3) });
      if (url === '/basket/merge') {
        expect(body).toEqual({
          items: [
            { bookId: 7, quantity: 2 },
            { bookId: 31, quantity: 1 },
          ],
        });
        expect(readGuestBasket()).toHaveLength(2); // not cleared before the call succeeds
        return Promise.resolve({
          data: {
            basket: basketOf(2),
            report: {
              adjustments: [
                { bookId: 31, title: 'Out of Print Atlas', reason: 'UNAVAILABLE', requestedQuantity: 1, addedQuantity: 0 },
              ],
            },
          },
        });
      }
      return Promise.reject(new Error(`unexpected ${url}`));
    });
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('3'));

    await userEvent.click(screen.getByText('login'));
    await waitFor(() => expect(screen.getByTestId('merge')).toHaveTextContent('1'));
    expect(signedIn).toBe(true);
    expect(screen.getByTestId('count')).toHaveTextContent('2');
    expect(readGuestBasket()).toEqual([]);
  });

  it('keeps the guest basket when the merge fails', async () => {
    writeGuestBasket([{ bookId: 7, quantity: 2 }]);
    apiClient.get.mockImplementation((url) =>
      url === '/auth/me' ? Promise.resolve({ data: customer }) : Promise.resolve({ data: basketOf(0) }),
    );
    apiClient.post.mockRejectedValue({ status: 0, code: 'NETWORK_ERROR', message: 'down', fieldErrors: [] });
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('who')).toHaveTextContent('Asha Verma'));
    await act(async () => {});
    expect(readGuestBasket()).toEqual([{ bookId: 7, quantity: 2 }]);
  });
});
