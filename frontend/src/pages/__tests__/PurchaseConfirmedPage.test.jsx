import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/orderApi.js', () => ({ getOrder: vi.fn() }));

import { getOrder } from '../../api/orderApi.js';
import { AuthContext } from '../../context/AuthContext.jsx';
import { BasketContext, EMPTY_BASKET } from '../../context/BasketContext.jsx';
import { apiErrors, order } from '../../test/fixtures/index.js';
import PurchaseConfirmedPage from '../PurchaseConfirmedPage.jsx';

function renderAt(path) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthContext.Provider value={{ customer: { id: 1 }, loading: false, login: vi.fn(), logout: vi.fn() }}>
        <BasketContext.Provider value={{ basket: EMPTY_BASKET, itemCount: 0, loading: false, error: null, refresh: vi.fn() }}>
        <Routes>
          <Route path="/purchase/confirmed/:orderId" element={<PurchaseConfirmedPage />} />
        </Routes>
        </BasketContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('PurchaseConfirmedPage', () => {
  it('shows the success message, the order reference and the purchased book (FR-CNF-01, FR-CNF-05)', async () => {
    getOrder.mockResolvedValue(order);
    renderAt('/purchase/confirmed/2');

    const panel = within(await screen.findByRole('region', { name: 'Purchase confirmation' }));
    expect(panel.getByText('Your purchase was successful')).toBeInTheDocument();
    expect(panel.getByText('BG-000002')).toBeInTheDocument();
    const item = within(panel.getByRole('listitem'));
    expect(item.getByRole('link', { name: 'The Habit Loop' })).toHaveAttribute('href', '/books/7');
    expect(item.getByRole('link', { name: 'Meera Iyer' })).toBeInTheDocument();
    expect(item.getByText('A practical guide to building small daily routines.')).toBeInTheDocument();
    expect(item.getByText(/Paperback · Self Help/)).toBeInTheDocument();
    expect(item.getByText('₹1,000.00')).toBeInTheDocument();
    expect(item.getByText('Delivery by Tue, 17 Nov')).toBeInTheDocument();
    expect(item.getByAltText('Cover of The Habit Loop')).toBeInTheDocument();
  });

  it('shows the totals, the delivery address and the payment status', async () => {
    getOrder.mockResolvedValue(order);
    renderAt('/purchase/confirmed/2');

    const totals = within(await screen.findByRole('region', { name: 'Order summary' }));
    expect(totals.getByText('Subtotal').nextSibling).toHaveTextContent('₹1,000.00');
    expect(totals.getByText('Tax (5%)').nextSibling).toHaveTextContent('₹50.00');
    expect(totals.getByText('Delivery').nextSibling).toHaveTextContent('₹40.00');
    expect(totals.getByText('Reward points').nextSibling).toHaveTextContent('₹100.00');
    expect(totals.getByText('Total paid or payable').nextSibling).toHaveTextContent('₹990.00');

    const address = within(screen.getByRole('region', { name: 'Delivery address' }));
    expect(address.getByText('Asha Verma')).toBeInTheDocument();
    expect(address.getByText('Bengaluru, Karnataka 560001')).toBeInTheDocument();
    expect(address.getByText('Phone: 9876543210')).toBeInTheDocument();

    const payment = within(screen.getByRole('region', { name: 'Payment' }));
    expect(payment.getByText(/Credit Card/)).toBeInTheDocument();
    expect(payment.getByText('Paid')).toBeInTheDocument();
  });

  it('shows a Cash on Delivery order with its payment as pending (FR-CNF-03)', async () => {
    getOrder.mockResolvedValue({ ...order, paymentMethod: 'COD', paymentStatus: 'PENDING_ON_DELIVERY', pointsRedeemed: 0 });
    renderAt('/purchase/confirmed/2');

    const payment = within(await screen.findByRole('region', { name: 'Payment' }));
    expect(payment.getByText(/Cash on Delivery/)).toBeInTheDocument();
    expect(payment.getByText('Pending (pay on delivery)')).toBeInTheDocument();
    expect(screen.queryByText('Reward points')).not.toBeInTheDocument();
  });

  it('offers Continue your Shopping, which returns to the catalogue (FR-CNF-02)', async () => {
    getOrder.mockResolvedValue(order);
    renderAt('/purchase/confirmed/2');

    expect(await screen.findByRole('link', { name: 'Continue your Shopping' })).toHaveAttribute('href', '/');
  });

  it('only reads the order, so a refresh cannot create another one (FR-CNF-06)', async () => {
    getOrder.mockResolvedValue(order);
    renderAt('/purchase/confirmed/2');
    await screen.findByText('Your purchase was successful');

    expect(getOrder).toHaveBeenCalledTimes(1);
  });

  it('shows not-found for another customer’s order and for an invalid id', async () => {
    getOrder.mockRejectedValueOnce(apiErrors.NOT_FOUND);
    renderAt('/purchase/confirmed/99');
    expect(await screen.findByText('Order not found')).toBeInTheDocument();
  });

  it('does not call the API for an invalid id', () => {
    renderAt('/purchase/confirmed/abc');

    expect(screen.getByText('Order not found')).toBeInTheDocument();
    expect(getOrder).not.toHaveBeenCalled();
  });

  it('shows an error with a retry when the order cannot be loaded', async () => {
    getOrder.mockRejectedValueOnce(apiErrors.INTERNAL_ERROR).mockResolvedValueOnce(order);
    const user = userEvent.setup();
    renderAt('/purchase/confirmed/2');

    await user.click(await screen.findByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('Your purchase was successful')).toBeInTheDocument();
  });
});
