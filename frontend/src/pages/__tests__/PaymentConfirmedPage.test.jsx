import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/orderApi.js', () => ({ getOrder: vi.fn() }));

import { getOrder } from '../../api/orderApi.js';
import { AuthContext } from '../../context/AuthContext.jsx';
import { BasketContext, EMPTY_BASKET } from '../../context/BasketContext.jsx';
import { apiErrors, order } from '../../test/fixtures/index.js';
import PaymentConfirmedPage from '../PaymentConfirmedPage.jsx';

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>;
}

function renderAt(path) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthContext.Provider value={{ customer: { id: 1 }, loading: false, login: vi.fn(), logout: vi.fn() }}>
        <BasketContext.Provider value={{ basket: EMPTY_BASKET, itemCount: 0, loading: false, error: null, refresh: vi.fn() }}>
        <Routes>
          <Route path="/payment/confirmed/:orderId" element={<PaymentConfirmedPage />} />
          <Route path="*" element={<Where />} />
        </Routes>
        </BasketContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('PaymentConfirmedPage', () => {
  it('confirms a successful online payment and continues to the purchase confirmation (FR-CNF-03)', async () => {
    getOrder.mockResolvedValue(order);
    renderAt('/payment/confirmed/2');

    const panel = within(await screen.findByRole('region', { name: 'Payment confirmation' }));
    expect(panel.getByText('Payment successful')).toBeInTheDocument();
    expect(panel.getByText(/₹990\.00 by Credit Card/)).toBeInTheDocument();
    expect(panel.getByText('Paid')).toBeInTheDocument();
    expect(panel.getByRole('link', { name: 'Continue' })).toHaveAttribute('href', '/purchase/confirmed/2');
    expect(getOrder).toHaveBeenCalledWith('2', expect.anything());
  });

  it('is not shown for Cash on Delivery: it goes straight to the purchase confirmation (FR-CNF-03)', async () => {
    getOrder.mockResolvedValue({ ...order, paymentMethod: 'COD', paymentStatus: 'PENDING_ON_DELIVERY' });
    renderAt('/payment/confirmed/2');

    expect(await screen.findByTestId('where')).toHaveTextContent('/purchase/confirmed/2');
    expect(screen.queryByText('Payment successful')).not.toBeInTheDocument();
  });

  it('shows not-found for an order that is not the customer’s', async () => {
    getOrder.mockRejectedValue(apiErrors.NOT_FOUND);
    renderAt('/payment/confirmed/99');

    expect(await screen.findByText('Order not found')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to the catalogue' })).toHaveAttribute('href', '/');
  });

  it('does not call the API for an invalid id', () => {
    renderAt('/payment/confirmed/abc');

    expect(screen.getByText('Order not found')).toBeInTheDocument();
    expect(getOrder).not.toHaveBeenCalled();
  });

  it('only reads the order, so a refresh cannot create another one (FR-CNF-06)', async () => {
    getOrder.mockResolvedValue(order);
    renderAt('/payment/confirmed/2');
    await screen.findByText('Payment successful');

    expect(getOrder).toHaveBeenCalledTimes(1);
  });

  it('shows an error with a retry when the order cannot be loaded', async () => {
    getOrder.mockRejectedValueOnce(apiErrors.INTERNAL_ERROR).mockResolvedValueOnce(order);
    const user = userEvent.setup();
    renderAt('/payment/confirmed/2');

    await user.click(await screen.findByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('Payment successful')).toBeInTheDocument();
  });
});
