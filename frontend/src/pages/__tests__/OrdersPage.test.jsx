import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/orderApi.js', () => ({ listOrders: vi.fn(), cancelOrder: vi.fn(), buyAgain: vi.fn() }));

import { buyAgain, cancelOrder, listOrders } from '../../api/orderApi.js';
import { AuthContext } from '../../context/AuthContext.jsx';
import { BasketContext, EMPTY_BASKET } from '../../context/BasketContext.jsx';
import { apiErrors, basket, ebook, order, orderSummary, paperback } from '../../test/fixtures/index.js';
import OrdersPage from '../OrdersPage.jsx';

const NOW = new Date('2026-11-12T12:00:00Z');

function renderPage() {
  const context = {
    basket: EMPTY_BASKET,
    itemCount: 0,
    loading: false,
    error: null,
    mergeReport: null,
    refresh: vi.fn().mockResolvedValue(undefined),
  };
  render(
    <MemoryRouter>
      <AuthContext.Provider value={{ customer: { id: 1 }, loading: false, login: vi.fn(), logout: vi.fn() }}>
        <BasketContext.Provider value={context}>
          <OrdersPage />
        </BasketContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
  return context;
}

const olderOrder = {
  ...orderSummary,
  id: 1,
  reference: 'BG-000001',
  placedAt: '2026-11-08T08:00:00Z',
  items: [{ book: ebook, quantity: 1, unitPrice: '299.00', lineTotal: '299.00' }],
  payableTotal: '313.95',
};
const cancelledSummary = { ...orderSummary, id: 3, reference: 'BG-000003', status: 'CANCELLED' };

beforeEach(() => {
  vi.clearAllMocks();
  vi.useFakeTimers({ toFake: ['Date'], now: NOW });
});

describe('OrdersPage', () => {
  it('lists the orders in the order the API returned them with reference, date, status, books and total (FR-ORD-01)', async () => {
    listOrders.mockResolvedValue([orderSummary, olderOrder]);
    renderPage();

    const cards = await screen.findAllByRole('article');
    expect(cards.map((card) => card.getAttribute('aria-label'))).toEqual(['Order BG-000002', 'Order BG-000001']);
    const first = within(cards[0]);
    expect(first.getByRole('link', { name: 'BG-000002' })).toHaveAttribute('href', '/orders/2');
    expect(first.getByText('Placed on 12 Nov 2026, 15:45')).toBeInTheDocument();
    expect(first.getByText('Placed')).toBeInTheDocument();
    expect(first.getByRole('link', { name: 'The Habit Loop' })).toHaveAttribute('href', '/books/7');
    expect(first.getByText('₹990.00')).toBeInTheDocument();
    expect(first.getByText('₹1,000.00')).toBeInTheDocument();
    expect(first.getByAltText('Cover of The Habit Loop')).toBeInTheDocument();
  });

  it('shows the heading and the orders\' own prices, not the catalogue price (snapshot)', async () => {
    listOrders.mockResolvedValue([{ ...orderSummary, items: [{ ...orderSummary.items[0], unitPrice: '450.00' }] }]);
    renderPage();

    const card = within(await screen.findByRole('article'));
    expect(screen.getByRole('heading', { name: 'My Orders' })).toBeInTheDocument();
    expect(card.getByText(/₹450\.00/)).toBeInTheDocument();
    expect(card.queryByText(/₹500\.00/)).not.toBeInTheDocument();
  });

  it('shows an empty state with a way back to the catalogue when there are no orders', async () => {
    listOrders.mockResolvedValue([]);
    renderPage();

    expect(await screen.findByText('You have no orders yet')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Start shopping' })).toHaveAttribute('href', '/');
    expect(screen.queryByRole('article')).not.toBeInTheDocument();
  });

  it('shows a spinner while loading and an error with Retry when the orders cannot be loaded', async () => {
    listOrders.mockRejectedValueOnce(apiErrors.INTERNAL_ERROR).mockResolvedValueOnce([orderSummary]);
    renderPage();

    expect(screen.getByRole('status')).toHaveTextContent('Loading your orders');
    expect(await screen.findByRole('alert')).toHaveTextContent('Your orders could not be loaded.');
    await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('article')).toBeInTheDocument();
    expect(listOrders).toHaveBeenCalledTimes(2);
  });

  it('offers Cancel order only for a placed order under 48 hours old (FR-ORD-04)', async () => {
    listOrders.mockResolvedValue([orderSummary, olderOrder, cancelledSummary]);
    renderPage();

    const cards = await screen.findAllByRole('article');
    expect(within(cards[0]).getByRole('button', { name: 'Cancel order' })).toBeInTheDocument(); // placed 2026-11-12
    expect(within(cards[1]).queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument(); // older than 48 h
    expect(within(cards[2]).queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument(); // cancelled
    expect(within(cards[2]).getByText('Cancelled')).toBeInTheDocument();
    expect(within(cards[2]).getByRole('button', { name: 'Buy Again' })).toBeInTheDocument();
  });

  it('treats exactly 48 hours as outside the window and 47:59:59 as inside', async () => {
    const exactly = { ...orderSummary, id: 5, reference: 'BG-000005', placedAt: '2026-11-10T12:00:00Z' };
    const justInside = { ...orderSummary, id: 6, reference: 'BG-000006', placedAt: '2026-11-10T12:00:01Z' };
    listOrders.mockResolvedValue([exactly, justInside]);
    renderPage();

    const cards = await screen.findAllByRole('article');
    expect(within(cards[0]).queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument();
    expect(within(cards[1]).getByRole('button', { name: 'Cancel order' })).toBeInTheDocument();
  });

  it('asks for confirmation before cancelling, keeps the order when the dialog is dismissed and cancels nothing yet', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    listOrders.mockResolvedValue([orderSummary]);
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Cancel order' }));
    const dialog = within(screen.getByRole('dialog', { name: 'Cancel order' }));
    expect(dialog.getByText(/This cannot be undone/)).toBeInTheDocument();
    expect(dialog.getByText('BG-000002')).toBeInTheDocument();
    expect(cancelOrder).not.toHaveBeenCalled();

    await user.click(dialog.getByRole('button', { name: 'Keep order' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(cancelOrder).not.toHaveBeenCalled();
  });

  it('cancels after confirmation, then reloads the list with the new status (FR-ORD-05)', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    listOrders.mockResolvedValueOnce([orderSummary]).mockResolvedValueOnce([{ ...orderSummary, status: 'CANCELLED' }]);
    cancelOrder.mockResolvedValue({ ...order, status: 'CANCELLED', cancellable: false, cancellableUntil: null });
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Cancel order' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Yes, cancel order' }));

    expect(cancelOrder).toHaveBeenCalledWith(2);
    expect(await screen.findByText('Order BG-000002 was cancelled.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    const card = within(await screen.findByRole('article'));
    expect(await card.findByText('Cancelled')).toBeInTheDocument();
    expect(card.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument();
  });

  it('shows the server message and reloads the list when the window has already expired', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    listOrders.mockResolvedValue([orderSummary]);
    cancelOrder.mockRejectedValue(apiErrors.CANCELLATION_WINDOW_EXPIRED);
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Cancel order' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Yes, cancel order' }));

    expect(await screen.findByText(apiErrors.CANCELLATION_WINDOW_EXPIRED.message)).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(listOrders).toHaveBeenCalledTimes(2);
    expect(screen.queryByText('Order BG-000002 was cancelled.')).not.toBeInTheDocument();
  });

  it('adds the books with Buy Again, refreshes the basket and says they were added (FR-ORD-03)', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    listOrders.mockResolvedValue([orderSummary]);
    buyAgain.mockResolvedValue({ basket, report: { adjustments: [] } });
    const context = renderPage();

    await user.click(await screen.findByRole('button', { name: 'Buy Again' }));

    expect(buyAgain).toHaveBeenCalledWith(2);
    const notice = await screen.findByRole('status', { name: 'Buy Again result' });
    expect(notice).toHaveTextContent('The books from order BG-000002 were added to your basket.');
    expect(within(notice).getByRole('link', { name: 'View basket' })).toHaveAttribute('href', '/basket');
    expect(context.refresh).toHaveBeenCalled();
  });

  it('lists the books that were not added or reduced after Buy Again', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    listOrders.mockResolvedValue([orderSummary]);
    buyAgain.mockResolvedValue({
      basket,
      report: {
        adjustments: [
          { bookId: 7, title: paperback.title, reason: 'QUANTITY_REDUCED', requestedQuantity: 2, addedQuantity: 1 },
          { bookId: 31, title: 'Out of Print Atlas', reason: 'UNAVAILABLE', requestedQuantity: 1, addedQuantity: 0 },
          { bookId: 40, title: 'The Withdrawn Book', reason: 'NOT_IN_CATALOGUE', requestedQuantity: 1, addedQuantity: 0 },
        ],
      },
    });
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Buy Again' }));

    const notice = within(await screen.findByRole('status', { name: 'Buy Again result' }));
    expect(notice.getByText('The Habit Loop was added with a lower quantity: you ordered 2 and 1 could be added.')).toBeInTheDocument();
    expect(notice.getByText('Out of Print Atlas is out of stock and was not added.')).toBeInTheDocument();
    expect(notice.getByText('The Withdrawn Book is no longer in the catalogue and was not added.')).toBeInTheDocument();
    await user.click(notice.getByRole('button', { name: 'Dismiss' }));
    expect(screen.queryByRole('status', { name: 'Buy Again result' })).not.toBeInTheDocument();
  });

  it('shows an error when Buy Again fails and leaves the order list in place', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    listOrders.mockResolvedValue([orderSummary]);
    buyAgain.mockRejectedValue(apiErrors.INTERNAL_ERROR);
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Buy Again' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(apiErrors.INTERNAL_ERROR.message);
    expect(screen.getByRole('article')).toBeInTheDocument();
  });
});
