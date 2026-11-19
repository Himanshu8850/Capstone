import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/addressApi.js', () => ({ listAddresses: vi.fn(), listStates: vi.fn() }));
vi.mock('../../api/orderApi.js', () => ({ quoteCheckout: vi.fn() }));
vi.mock('../../api/rewardApi.js', () => ({ getRewardAccount: vi.fn() }));

import * as addressApi from '../../api/addressApi.js';
import * as orderApi from '../../api/orderApi.js';
import * as rewardApi from '../../api/rewardApi.js';
import { AuthContext } from '../../context/AuthContext.jsx';
import { BasketContext, EMPTY_BASKET } from '../../context/BasketContext.jsx';
import { address, apiErrors, basket, checkoutQuote, ebook } from '../../test/fixtures/index.js';
import CheckoutPage from '../CheckoutPage.jsx';

function PaymentProbe() {
  const location = useLocation();
  return <p data-testid="payment-state">{JSON.stringify(location.state)}</p>;
}

function renderPage(basketValue = basket, extra = {}) {
  const context = {
    basket: basketValue,
    itemCount: basketValue.itemCount,
    loading: false,
    error: null,
    refresh: vi.fn(),
    ...extra,
  };
  render(
    <MemoryRouter initialEntries={['/checkout']}>
      <AuthContext.Provider value={{ customer: { id: 1 }, loading: false, login: vi.fn(), logout: vi.fn() }}>
        <BasketContext.Provider value={context}>
          <Routes>
            <Route path="/checkout" element={<CheckoutPage />} />
            <Route path="/payment" element={<PaymentProbe />} />
          </Routes>
        </BasketContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
  return context;
}

const STATES = ['Karnataka', 'Kerala', 'Maharashtra'];

async function fillAddress(user) {
  await user.type(screen.getByLabelText('Full name *'), 'Asha Verma');
  await user.type(screen.getByLabelText('Phone *'), '9876543210');
  await user.type(screen.getByLabelText('Address line 1 *'), '12 Lake View Road');
  await user.type(screen.getByLabelText('City *'), 'Bengaluru');
  await user.selectOptions(screen.getByLabelText('State *'), 'Karnataka');
  await user.type(screen.getByLabelText('PIN code *'), '560001');
}

beforeEach(() => {
  vi.clearAllMocks();
  addressApi.listStates.mockResolvedValue(STATES);
  addressApi.listAddresses.mockResolvedValue([]);
  rewardApi.getRewardAccount.mockResolvedValue({ balance: 1000 });
  orderApi.quoteCheckout.mockResolvedValue({ ...checkoutQuote, pointsToRedeem: 0, payableTotal: '1090.00' });
});

describe('CheckoutPage', () => {
  it('shows the basket, the address fields with India as the country, the summary and Pay Now (FR-CHK-01, FR-CHK-02)', async () => {
    renderPage();

    expect(await screen.findByRole('region', { name: 'Order summary' })).toBeInTheDocument();
    const items = within(screen.getByRole('region', { name: 'Your basket' }));
    expect(items.getByText('The Habit Loop')).toBeInTheDocument();
    for (const label of ['Full name *', 'Phone *', 'Address line 1 *', 'Address line 2 (optional)', 'City *', 'State *', 'PIN code *', 'Country *']) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }
    expect(screen.getByLabelText('Country *')).toHaveValue('India');
    expect(screen.getByRole('button', { name: 'Pay Now' })).toBeEnabled();
    expect(screen.getByRole('navigation', { name: 'Breadcrumb' })).toBeInTheDocument();
  });

  it('asks for no delivery address when the basket holds only e-books, and sends none (AMD-004)', async () => {
    const ebookBasket = {
      items: [{ book: ebook, quantity: 1, lineTotal: '299.00' }],
      itemCount: 1,
      subtotal: '299.00',
      tax: '14.95',
      deliveryCharge: '0.00',
      total: '313.95',
    };
    orderApi.quoteCheckout.mockResolvedValue({
      ...checkoutQuote,
      subtotal: '299.00',
      tax: '14.95',
      deliveryCharge: '0.00',
      total: '313.95',
      pointsToRedeem: 0,
      payableTotal: '313.95',
    });
    const user = userEvent.setup();
    renderPage(ebookBasket);

    await screen.findByRole('region', { name: 'Order summary' });
    expect(screen.queryByLabelText('Full name *')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Phone *')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Address line 1 *')).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Delivery address' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Use a saved address')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Pay Now' }));
    const state = JSON.parse((await screen.findByTestId('payment-state')).textContent);
    expect(state).toEqual({ pointsToRedeem: 0, payableTotal: '313.95' });
  });

  it('still asks for the address when an e-book is mixed with a physical book', async () => {
    renderPage({ ...basket, items: [...basket.items, { book: ebook, quantity: 1, lineTotal: '299.00' }] });

    await screen.findByRole('region', { name: 'Order summary' });
    expect(screen.getByLabelText('Full name *')).toBeInTheDocument();
  });

  it('has no e-mail, first-name, last-name or coupon field (FR-CHK-02, FR-CHK-06)', async () => {
    renderPage();
    await screen.findByRole('region', { name: 'Order summary' });

    expect(screen.queryByLabelText(/e-?mail/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/first name|last name/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/coupon|discount/i)).not.toBeInTheDocument();
  });

  it('shows the summary exactly as quoted: subtotal, tax, delivery and payable total (FR-CHK-05)', async () => {
    renderPage();

    const summary = within(await screen.findByRole('region', { name: 'Order summary' }));
    expect(summary.getByText('Subtotal').nextSibling).toHaveTextContent('₹1,000.00');
    expect(summary.getByText('Tax (5%)').nextSibling).toHaveTextContent('₹50.00');
    expect(summary.getByText('Delivery').nextSibling).toHaveTextContent('₹40.00');
    expect(summary.getByText('Payable total').nextSibling).toHaveTextContent('₹1,090.00');
    expect(summary.queryByText('Reward points')).not.toBeInTheDocument();
    expect(orderApi.quoteCheckout).toHaveBeenCalledWith(0);
  });

  it('explains that no address is saved when there are none (FR-CHK-03)', async () => {
    renderPage();

    expect(await screen.findByText(/You have no saved addresses yet/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Use a saved address')).not.toBeInTheDocument();
  });

  it('fills the form from a saved address and lets it be edited (FR-CHK-03)', async () => {
    addressApi.listAddresses.mockResolvedValue([address]);
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions(await screen.findByLabelText('Use a saved address'), '1');

    expect(screen.getByLabelText('Full name *')).toHaveValue('Asha Verma');
    expect(screen.getByLabelText('Phone *')).toHaveValue('9876543210');
    expect(screen.getByLabelText('Address line 2 (optional)')).toHaveValue('Near City Park');
    expect(screen.getByLabelText('State *')).toHaveValue('Karnataka');
    await user.clear(screen.getByLabelText('City *'));
    await user.type(screen.getByLabelText('City *'), 'Mysuru');
    expect(screen.getByLabelText('City *')).toHaveValue('Mysuru');
  });

  it('blocks Pay Now with an inline error for each invalid field (FR-CHK-04, FR-CHK-08)', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('region', { name: 'Order summary' });

    await user.type(screen.getByLabelText('Phone *'), '12345');
    await user.type(screen.getByLabelText('PIN code *'), '1234');
    await user.click(screen.getByRole('button', { name: 'Pay Now' }));

    expect(screen.getByText('Enter the full name.')).toBeInTheDocument();
    expect(screen.getByText('Phone must be exactly 10 digits.')).toBeInTheDocument();
    expect(screen.getByText('Enter address line 1.')).toBeInTheDocument();
    expect(screen.getByText('Enter the city.')).toBeInTheDocument();
    expect(screen.getByText('Enter the state.')).toBeInTheDocument();
    expect(screen.getByText('PIN code must be exactly 6 digits.')).toBeInTheDocument();
    expect(screen.queryByTestId('payment-state')).not.toBeInTheDocument();
  });

  it('moves on to payment with the address and the amount when everything is valid (FR-CHK-08)', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('region', { name: 'Order summary' });

    await fillAddress(user);
    await user.click(screen.getByRole('button', { name: 'Pay Now' }));

    const state = JSON.parse((await screen.findByTestId('payment-state')).textContent);
    expect(state).toEqual({
      address: {
        fullName: 'Asha Verma',
        phone: '9876543210',
        addressLine1: '12 Lake View Road',
        city: 'Bengaluru',
        state: 'Karnataka',
        pinCode: '560001',
        country: 'India',
      },
      pointsToRedeem: 0,
      payableTotal: '1090.00',
    });
  });

  it('asks for a state name as free text when the country is not India', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('region', { name: 'Order summary' });

    await user.clear(screen.getByLabelText('Country *'));
    await user.type(screen.getByLabelText('Country *'), 'United Kingdom');

    expect(screen.getByLabelText('State *').tagName).toBe('INPUT');
  });

  it('disables Pay Now and lists a basket problem reported by the quote (FR-BSK-08)', async () => {
    orderApi.quoteCheckout.mockResolvedValue({
      ...checkoutQuote,
      pointsToRedeem: 0,
      issues: [
        { bookId: 7, title: 'The Habit Loop', type: 'QUANTITY_REDUCED', message: 'Only 1 copy is available. Reduce the quantity.' },
      ],
    });
    renderPage();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('The Habit Loop: Only 1 copy is available. Reduce the quantity.');
    expect(within(alert).getByRole('link', { name: 'Review your basket' })).toHaveAttribute('href', '/basket');
    expect(screen.getByRole('button', { name: 'Pay Now' })).toBeDisabled();
  });

  it('shows the reason when the quote fails and lets the customer retry', async () => {
    orderApi.quoteCheckout.mockRejectedValueOnce(apiErrors.EMPTY_BASKET).mockResolvedValue({ ...checkoutQuote, pointsToRedeem: 0 });
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Your basket is empty.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('region', { name: 'Order summary' })).toBeInTheDocument();
  });

  it('shows an empty state instead of the form when the basket is empty (FR-CHK-01)', async () => {
    renderPage(EMPTY_BASKET);

    expect(await screen.findByText('Your basket is empty')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Continue shopping' })).toHaveAttribute('href', '/');
    expect(screen.queryByRole('button', { name: 'Pay Now' })).not.toBeInTheDocument();
    expect(orderApi.quoteCheckout).not.toHaveBeenCalled();
  });

  it('shows a spinner while the basket loads', async () => {
    renderPage(EMPTY_BASKET, { loading: true });
    expect(screen.getByRole('status')).toHaveTextContent('Loading your basket');
    await waitFor(() => expect(addressApi.listStates).toHaveBeenCalled());
  });

  it('offers the basket error with a retry', async () => {
    const context = renderPage(EMPTY_BASKET, { error: apiErrors.INTERNAL_ERROR });
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(context.refresh).toHaveBeenCalled();
  });
});
