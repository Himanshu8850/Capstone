import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/axiosClient.js', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import apiClient from '../../api/axiosClient.js';
import { AuthProvider, useAuth } from '../AuthContext.jsx';
import { BasketProvider, useBasketContext } from '../BasketContext.jsx';
import { readGuestBasket, writeGuestBasket } from '../../utils/storage.js';
import { PASSWORD } from '../../test/credentials';

const customer = { id: 1, fullName: 'Asha Verma', email: 'asha.verma@example.com' };
const unauthenticated = { status: 401, code: 'UNAUTHENTICATED', message: 'Log in', fieldErrors: [] };

const basketOf = (quantity, stockQuantity = 25) => ({
  items: [{ book: { id: 7, title: 'The Habit Loop', stockQuantity }, quantity, lineTotal: '500.00' }],
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
      <p data-testid="who">{auth.loading ? 'loading' : auth.customer ? 'customer' : 'guest'}</p>
      <p data-testid="count">{basket.loading ? 'loading' : basket.itemCount}</p>
      <p data-testid="error">{basket.error ? basket.error.code : 'none'}</p>
      <p data-testid="report">{basket.mergeReport ? JSON.stringify(basket.mergeReport.adjustments) : 'none'}</p>
      <button onClick={() => auth.login('asha.verma@example.com', PASSWORD)}>login</button>
      <button onClick={() => basket.refresh()}>refresh</button>
      <button onClick={() => basket.addItem(7, 1).catch((e) => (window.lastError = e))}>add</button>
      <button onClick={() => basket.updateItem(7, 4).catch((e) => (window.lastError = e))}>update</button>
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

beforeEach(() => {
  vi.clearAllMocks();
  window.lastError = undefined;
});

describe('guest basket merge at login (PL-01)', () => {
  function signInFlow({ mergeResponse }) {
    apiClient.get.mockImplementation((url) =>
      url === '/auth/me' ? Promise.reject(unauthenticated) : Promise.resolve({ data: basketOf(0) }),
    );
    apiClient.post.mockImplementation((url) => {
      if (url === '/auth/login') return Promise.resolve({ data: customer });
      if (url === '/basket/preview') return Promise.resolve({ data: basketOf(2) });
      if (url === '/basket/merge') return mergeResponse();
      return Promise.reject(new Error(`unexpected ${url}`));
    });
  }

  it('clears the guest basket only after the merge succeeded', async () => {
    writeGuestBasket([{ bookId: 7, quantity: 2 }]);
    let guestLinesDuringMerge;
    signInFlow({
      mergeResponse: () => {
        guestLinesDuringMerge = readGuestBasket();
        return Promise.resolve({ data: { basket: basketOf(5), report: { adjustments: [] } } });
      },
    });
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('2'));

    await userEvent.click(screen.getByText('login'));
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('5'));

    expect(guestLinesDuringMerge).toEqual([{ bookId: 7, quantity: 2 }]);
    expect(readGuestBasket()).toEqual([]);
    expect(screen.getByTestId('report')).toHaveTextContent('none');
  });

  it('keeps the guest basket and shows an error when the merge fails, so nothing is lost', async () => {
    writeGuestBasket([{ bookId: 7, quantity: 2 }]);
    signInFlow({ mergeResponse: () => Promise.reject({ status: 0, code: 'NETWORK_ERROR', message: 'down', fieldErrors: [] }) });
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('2'));

    await userEvent.click(screen.getByText('login'));
    await waitFor(() => expect(screen.getByTestId('error')).toHaveTextContent('NETWORK_ERROR'));

    expect(readGuestBasket()).toEqual([{ bookId: 7, quantity: 2 }]);
  });

  it('retries the merge on refresh and clears the guest basket once it works', async () => {
    writeGuestBasket([{ bookId: 7, quantity: 2 }]);
    let attempts = 0;
    signInFlow({
      mergeResponse: () => {
        attempts += 1;
        return attempts === 1
          ? Promise.reject({ status: 500, code: 'INTERNAL_ERROR', message: 'x', fieldErrors: [] })
          : Promise.resolve({ data: { basket: basketOf(2), report: { adjustments: [] } } });
      },
    });
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('2'));
    await userEvent.click(screen.getByText('login'));
    await waitFor(() => expect(screen.getByTestId('error')).toHaveTextContent('INTERNAL_ERROR'));

    await userEvent.click(screen.getByText('refresh'));
    await waitFor(() => expect(screen.getByTestId('error')).toHaveTextContent('none'));

    expect(attempts).toBe(2);
    expect(readGuestBasket()).toEqual([]);
  });

  it('surfaces the report when the merge adjusted something and no report when it did not', async () => {
    writeGuestBasket([{ bookId: 31, quantity: 1 }]);
    const adjustments = [
      { bookId: 31, title: 'Out of Print Atlas', reason: 'UNAVAILABLE', requestedQuantity: 1, addedQuantity: 0 },
    ];
    signInFlow({ mergeResponse: () => Promise.resolve({ data: { basket: basketOf(1), report: { adjustments } } }) });
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('count')).not.toHaveTextContent('loading'));

    await userEvent.click(screen.getByText('login'));
    await waitFor(() => expect(screen.getByTestId('report')).toHaveTextContent('UNAVAILABLE'));
  });
});

describe('guest limits checked in the browser', () => {
  beforeEach(() => {
    apiClient.get.mockRejectedValue(unauthenticated);
  });

  it('rejects an add that exceeds the stock of a physical book and stores nothing', async () => {
    apiClient.post.mockResolvedValue({ data: basketOf(1, 0) });
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('0'));

    await userEvent.click(screen.getByText('add'));
    await waitFor(() => expect(window.lastError).toMatchObject({ code: 'OUT_OF_STOCK' }));
    expect(readGuestBasket()).toEqual([]);
  });

  it('stores a valid update after the preview confirms it is within the limits', async () => {
    writeGuestBasket([{ bookId: 7, quantity: 2 }]);
    apiClient.post.mockImplementation((url, body) =>
      Promise.resolve({ data: basketOf(body.items[0].quantity) }),
    );
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('2'));

    await userEvent.click(screen.getByText('update')); // sets 4 units
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('4'));
    expect(readGuestBasket()).toEqual([{ bookId: 7, quantity: 4 }]);
    expect(window.lastError).toBeUndefined();
  });

  it('rejects an update above the stock of a physical book and keeps the stored quantity', async () => {
    writeGuestBasket([{ bookId: 7, quantity: 2 }]);
    apiClient.post.mockImplementation((url, body) =>
      Promise.resolve({ data: basketOf(body.items[0].quantity, 3) }),
    );
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('count')).toHaveTextContent('2'));

    await userEvent.click(screen.getByText('update')); // asks for 4, only 3 in stock
    await waitFor(() => expect(window.lastError).toMatchObject({ code: 'OUT_OF_STOCK' }));
    expect(readGuestBasket()).toEqual([{ bookId: 7, quantity: 2 }]);
  });
});
