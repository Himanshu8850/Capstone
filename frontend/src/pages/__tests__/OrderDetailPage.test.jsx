import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/orderApi.js', () => ({ getOrder: vi.fn(), cancelOrder: vi.fn(), buyAgain: vi.fn() }));

import { buyAgain, cancelOrder, getOrder } from '../../api/orderApi.js';
import { AuthContext } from '../../context/AuthContext.jsx';
import { BasketContext, EMPTY_BASKET } from '../../context/BasketContext.jsx';
import { apiErrors, basket, cancelledOrder, order } from '../../test/fixtures/index.js';
import OrderDetailPage from '../OrderDetailPage.jsx';

const expiredOrder = { ...order, cancellable: false };

function renderAt(path) {
  const context = {
    basket: EMPTY_BASKET,
    itemCount: 0,
    loading: false,
    error: null,
    mergeReport: null,
    refresh: vi.fn().mockResolvedValue(undefined),
  };
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthContext.Provider value={{ customer: { id: 1 }, loading: false, login: vi.fn(), logout: vi.fn() }}>
        <BasketContext.Provider value={context}>
          <Routes>
            <Route path="/orders/:id" element={<OrderDetailPage />} />
          </Routes>
        </BasketContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
  return context;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('OrderDetailPage', () => {
  it('shows the order as it was placed: reference, status, books, totals, address and payment (FR-ORD-02)', async () => {
    getOrder.mockResolvedValue(order);
    renderAt('/orders/2');

    const page = within(await screen.findByRole('region', { name: 'Order details' }));
    expect(page.getByText('BG-000002')).toBeInTheDocument();
    expect(page.getByText('Placed')).toBeInTheDocument();
    expect(page.getByRole('link', { name: 'The Habit Loop' })).toHaveAttribute('href', '/books/7');
    expect(page.getByText(/2 × ₹500\.00/)).toBeInTheDocument();
    const totals = within(page.getByRole('region', { name: 'Order summary' }));
    expect(totals.getByText('Subtotal').nextSibling).toHaveTextContent('₹1,000.00');
    expect(totals.getByText('Tax (5%)').nextSibling).toHaveTextContent('₹50.00');
    expect(totals.getByText('Delivery').nextSibling).toHaveTextContent('₹40.00');
    expect(totals.getByText('Reward points').nextSibling).toHaveTextContent('₹100.00');
    expect(totals.getByText('Total').nextSibling).toHaveTextContent('₹990.00');
    expect(page.getByText('12 Lake View Road')).toBeInTheDocument();
    expect(page.getByText('Credit Card', { exact: false })).toBeInTheDocument();
    expect(page.getByText('Paid')).toBeInTheDocument();
    expect(getOrder).toHaveBeenCalledWith('2', expect.anything());
  });

  it('offers Cancel order and says until when while the order is cancellable (FR-ORD-04)', async () => {
    getOrder.mockResolvedValue(order);
    renderAt('/orders/2');

    expect(await screen.findByRole('button', { name: 'Cancel order' })).toBeInTheDocument();
    expect(screen.getByText(/You can cancel this order until 14 Nov 2026, 15:45\./)).toBeInTheDocument();
  });

  it('hides Cancel order and explains why once 48 hours have passed', async () => {
    getOrder.mockResolvedValue(expiredOrder);
    renderAt('/orders/2');

    expect(await screen.findByText('This order can no longer be cancelled because 48 hours have passed.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument();
  });

  it('hides Cancel order for an order that is already cancelled and shows the refunded payment', async () => {
    getOrder.mockResolvedValue(cancelledOrder);
    renderAt('/orders/2');

    expect(await screen.findByText('This order was cancelled and cannot be changed.')).toBeInTheDocument();
    const page = within(screen.getByRole('region', { name: 'Order details' }));
    expect(page.getByText('Cancelled')).toBeInTheDocument();
    expect(page.getByText('Refunded')).toBeInTheDocument();
    expect(page.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument();
  });

  it('opens a confirmation dialog first and only cancels when the customer confirms (FR-ORD-05)', async () => {
    const user = userEvent.setup();
    getOrder.mockResolvedValue(order);
    cancelOrder.mockResolvedValue(cancelledOrder);
    renderAt('/orders/2');

    await user.click(await screen.findByRole('button', { name: 'Cancel order' }));
    const dialog = within(screen.getByRole('dialog', { name: 'Cancel order' }));
    expect(dialog.getByText(/This cannot be undone/)).toBeInTheDocument();
    expect(cancelOrder).not.toHaveBeenCalled();

    await user.click(dialog.getByRole('button', { name: 'Keep order' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(cancelOrder).not.toHaveBeenCalled();
    expect(screen.getByText('Placed')).toBeInTheDocument();
  });

  it('shows the cancelled order with every field refreshed after confirming', async () => {
    const user = userEvent.setup();
    getOrder.mockResolvedValue(order);
    cancelOrder.mockResolvedValue(cancelledOrder);
    renderAt('/orders/2');

    await user.click(await screen.findByRole('button', { name: 'Cancel order' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Yes, cancel order' }));

    expect(cancelOrder).toHaveBeenCalledWith(2);
    expect(await screen.findByText('Order BG-000002 was cancelled.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    const page = within(screen.getByRole('region', { name: 'Order details' }));
    expect(page.getByText('Cancelled')).toBeInTheDocument();
    expect(page.getByText('Refunded')).toBeInTheDocument();
    expect(page.getByText('This order was cancelled and cannot be changed.')).toBeInTheDocument();
    expect(page.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument();
    expect(getOrder).toHaveBeenCalledTimes(1);
  });

  it('keeps the dialog open with the server message and reloads the order when the window has expired', async () => {
    const user = userEvent.setup();
    getOrder.mockResolvedValueOnce(order).mockResolvedValueOnce(expiredOrder);
    cancelOrder.mockRejectedValue(apiErrors.CANCELLATION_WINDOW_EXPIRED);
    renderAt('/orders/2');

    await user.click(await screen.findByRole('button', { name: 'Cancel order' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Yes, cancel order' }));

    expect(await within(screen.getByRole('dialog')).findByText(apiErrors.CANCELLATION_WINDOW_EXPIRED.message)).toBeInTheDocument();
    expect(getOrder).toHaveBeenCalledTimes(2);
    const page = within(screen.getByRole('region', { name: 'Order details' }));
    expect(await page.findByText('This order can no longer be cancelled because 48 hours have passed.')).toBeInTheDocument();
  });

  it('reports an order that was already cancelled elsewhere (second cancel)', async () => {
    const user = userEvent.setup();
    getOrder.mockResolvedValueOnce(order).mockResolvedValueOnce(cancelledOrder);
    cancelOrder.mockRejectedValue(apiErrors.ALREADY_CANCELLED);
    renderAt('/orders/2');

    await user.click(await screen.findByRole('button', { name: 'Cancel order' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Yes, cancel order' }));

    expect(await within(screen.getByRole('dialog')).findByText(apiErrors.ALREADY_CANCELLED.message)).toBeInTheDocument();
    expect(await screen.findByText('This order was cancelled and cannot be changed.')).toBeInTheDocument();
  });

  it('adds the books with Buy Again and lists what was not added (FR-ORD-03)', async () => {
    const user = userEvent.setup();
    getOrder.mockResolvedValue(order);
    buyAgain.mockResolvedValue({
      basket,
      report: {
        adjustments: [
          { bookId: 7, title: 'The Habit Loop', reason: 'UNAVAILABLE', requestedQuantity: 2, addedQuantity: 0 },
        ],
      },
    });
    const context = renderAt('/orders/2');

    await user.click(await screen.findByRole('button', { name: 'Buy Again' }));

    expect(buyAgain).toHaveBeenCalledWith(2);
    const notice = await screen.findByRole('status', { name: 'Buy Again result' });
    expect(within(notice).getByText('The Habit Loop is out of stock and was not added.')).toBeInTheDocument();
    expect(context.refresh).toHaveBeenCalled();
  });

  it('shows "Order not found" for an unknown or foreign order (404) and for a malformed id', async () => {
    getOrder.mockRejectedValue(apiErrors.NOT_FOUND);
    renderAt('/orders/999');
    expect(await screen.findByText('Order not found')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to My Orders' })).toHaveAttribute('href', '/orders');
  });

  it('does not call the API for a malformed id', async () => {
    renderAt('/orders/abc');
    expect(await screen.findByText('Order not found')).toBeInTheDocument();
    expect(getOrder).not.toHaveBeenCalled();
  });

  it('shows an error with Retry when the order cannot be loaded', async () => {
    const user = userEvent.setup();
    getOrder.mockRejectedValueOnce(apiErrors.INTERNAL_ERROR).mockResolvedValueOnce(order);
    renderAt('/orders/2');

    expect(await screen.findByRole('alert')).toHaveTextContent('Your order could not be loaded.');
    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('region', { name: 'Order details' })).toBeInTheDocument();
  });
});
