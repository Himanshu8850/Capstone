import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../context/AuthContext.jsx';
import { BasketContext, EMPTY_BASKET } from '../../context/BasketContext.jsx';
import { basket, ebook, outOfStock, paperback } from '../../test/fixtures/index.js';
import BasketPage from '../BasketPage.jsx';

function renderPage(value = {}) {
  const context = {
    basket,
    itemCount: basket.itemCount,
    loading: false,
    error: null,
    mergeReport: null,
    addItem: vi.fn(),
    updateItem: vi.fn().mockResolvedValue(undefined),
    removeItem: vi.fn().mockResolvedValue(undefined),
    refresh: vi.fn(),
    dismissMergeReport: vi.fn(),
    ...value,
  };
  render(
    <MemoryRouter>
      <AuthContext.Provider value={{ customer: null, loading: false, login: vi.fn(), logout: vi.fn() }}>
        <BasketContext.Provider value={context}>
          <BasketPage />
        </BasketContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
  return context;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('BasketPage', () => {
  it('lists each line with cover, title, author, format, unit price, delivery date, quantity and line total (FR-BSK-02)', () => {
    renderPage();

    const line = screen.getByRole('listitem');
    expect(within(line).getByRole('link', { name: 'The Habit Loop' })).toHaveAttribute('href', '/books/7');
    expect(within(line).getByRole('link', { name: 'Meera Iyer' })).toHaveAttribute('href', '/?author=Meera+Iyer');
    expect(within(line).getByText(/Paperback/)).toBeInTheDocument();
    expect(within(line).getByText(/₹500\.00 each/)).toBeInTheDocument();
    expect(within(line).getByText('Delivery by Tue, 17 Nov')).toBeInTheDocument();
    expect(within(line).getByLabelText('Quantity 2')).toBeInTheDocument();
    expect(within(line).getByText('₹1,000.00')).toBeInTheDocument();
  });

  it('shows the totals breakdown exactly as the API returned it (FR-BSK-04)', () => {
    renderPage();

    const totals = screen.getByRole('region', { name: 'Order totals' });
    expect(within(totals).getByText('Subtotal').nextSibling).toHaveTextContent('₹1,000.00');
    expect(within(totals).getByText('Tax (5%)').nextSibling).toHaveTextContent('₹50.00');
    expect(within(totals).getByText('Delivery').nextSibling).toHaveTextContent('₹40.00');
    expect(within(totals).getByText('Total').nextSibling).toHaveTextContent('₹1,090.00');
  });

  it('shows free delivery for an eBook-only basket', () => {
    renderPage({
      basket: {
        items: [{ book: ebook, quantity: 1, lineTotal: '299.00' }],
        itemCount: 1,
        subtotal: '299.00',
        tax: '14.95',
        deliveryCharge: '0.00',
        total: '313.95',
      },
    });
    const totals = screen.getByRole('region', { name: 'Order totals' });
    expect(within(totals).getByText('Delivery').nextSibling).toHaveTextContent('₹0.00');
  });

  it('changes the quantity with the stepper', async () => {
    const context = renderPage();

    await userEvent.click(screen.getByRole('button', { name: 'Increase quantity of The Habit Loop' }));
    expect(context.updateItem).toHaveBeenCalledWith(7, 3);
    await userEvent.click(screen.getByRole('button', { name: 'Decrease quantity of The Habit Loop' }));
    expect(context.updateItem).toHaveBeenCalledWith(7, 1);
  });

  it('cannot go below 1 (use Remove instead) or above 10 (FR-BSK-03)', () => {
    renderPage({ basket: { ...basket, items: [{ book: paperback, quantity: 1, lineTotal: '500.00' }] } });
    expect(screen.getByRole('button', { name: 'Decrease quantity of The Habit Loop' })).toBeDisabled();
  });

  it('disables Increase at 10 units', () => {
    renderPage({ basket: { ...basket, items: [{ book: paperback, quantity: 10, lineTotal: '5000.00' }] } });
    expect(screen.getByRole('button', { name: 'Increase quantity of The Habit Loop' })).toBeDisabled();
  });

  it('disables Increase at the stock of a physical book', () => {
    const scarce = { ...paperback, stockQuantity: 3 };
    renderPage({ basket: { ...basket, items: [{ book: scarce, quantity: 3, lineTotal: '1500.00' }] } });
    expect(screen.getByRole('button', { name: 'Increase quantity of The Habit Loop' })).toBeDisabled();
  });

  it('does not limit an eBook by stock', () => {
    renderPage({ basket: { ...basket, items: [{ book: ebook, quantity: 5, lineTotal: '1495.00' }] } });
    expect(screen.getByRole('button', { name: 'Increase quantity of Quiet Mornings' })).toBeEnabled();
  });

  it('removes a line', async () => {
    const context = renderPage();
    await userEvent.click(screen.getByRole('button', { name: /Remove/ }));
    expect(context.removeItem).toHaveBeenCalledWith(7);
  });

  it('shows the reason when the server rejects a change and keeps the basket on screen', async () => {
    const updateItem = vi.fn().mockRejectedValue({ code: 'OUT_OF_STOCK', message: 'This book is out of stock.' });
    renderPage({ updateItem });
    await userEvent.click(screen.getByRole('button', { name: 'Increase quantity of The Habit Loop' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('This book is out of stock.');
    expect(screen.getByRole('heading', { name: 'The Habit Loop' })).toBeInTheDocument();
  });

  it('marks a book that is no longer available', () => {
    renderPage({
      basket: { ...basket, items: [{ book: outOfStock, quantity: 1, lineTotal: '500.00' }] },
    });
    expect(screen.getByText('Unavailable')).toBeInTheDocument();
  });

  it('shows an empty state with a way back when the basket is empty (FR-BSK-05)', () => {
    renderPage({ basket: EMPTY_BASKET, itemCount: 0 });
    expect(screen.getByRole('heading', { name: 'Your basket is empty' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Continue shopping' })).toHaveAttribute('href', '/');
    expect(screen.queryByRole('link', { name: 'Proceed to Checkout' })).not.toBeInTheDocument();
  });

  it('proceeds to checkout, which asks a guest to log in and returns (A-12)', () => {
    renderPage();
    expect(screen.getByRole('link', { name: 'Proceed to Checkout' })).toHaveAttribute('href', '/checkout');
  });

  it('shows a loading indicator while the first load is running', () => {
    renderPage({ basket: EMPTY_BASKET, loading: true });
    expect(screen.getByText('Loading your basket')).toBeInTheDocument();
  });

  it('shows an error with Retry when the basket cannot be loaded', async () => {
    const context = renderPage({ error: { code: 'NETWORK_ERROR' }, basket: EMPTY_BASKET });
    expect(screen.getByRole('alert')).toHaveTextContent('Your basket could not be loaded.');
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(context.refresh).toHaveBeenCalled();
  });

  it('tells the customer what the merge adjusted and lets them dismiss it (FR-BSK-08)', async () => {
    const report = {
      adjustments: [
        { bookId: 31, title: 'Out of Print Atlas', reason: 'UNAVAILABLE', requestedQuantity: 1, addedQuantity: 0 },
        { bookId: 7, title: 'The Habit Loop', reason: 'QUANTITY_REDUCED', requestedQuantity: 8, addedQuantity: 3 },
      ],
    };
    const context = renderPage({ mergeReport: report });

    const notice = screen.getByRole('status', { name: 'Basket update' });
    expect(within(notice).getByText('Out of Print Atlas is out of stock and was not added.')).toBeInTheDocument();
    expect(
      within(notice).getByText('The Habit Loop was added with a lower quantity: you asked for 8 and 3 could be added.'),
    ).toBeInTheDocument();
    await userEvent.click(within(notice).getByRole('button', { name: 'Dismiss' }));
    expect(context.dismissMergeReport).toHaveBeenCalled();
  });

  it('shows no coupon or wallet control (CL-04, CL-05, CL-06)', () => {
    renderPage();
    expect(screen.queryByText(/coupon|wallet/i)).not.toBeInTheDocument();
  });
});
