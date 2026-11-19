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
import { checkPoints } from '../../components/checkout/RewardsRedemption.jsx';
import { AuthContext } from '../../context/AuthContext.jsx';
import { BasketContext } from '../../context/BasketContext.jsx';
import { basket, checkoutQuote } from '../../test/fixtures/index.js';
import { subtractPoints } from '../../utils/money.js';
import CheckoutPage from '../CheckoutPage.jsx';

/**
 * Reward-point redemption on the checkout page (T128, FR-CHK-07, FR-RWD-01..03, 05): the balance and the maximum are
 * shown below the order summary, the typed points reduce the payable total at once, a number above the maximum is
 * explained and blocks Pay Now, zero and an empty box are allowed, and the chosen points travel to the payment page.
 * The fixture total is 1090.00 (subtotal 1000.00 + tax 50.00 + delivery 40.00), balance 1000, maximum 545.
 */

function PaymentProbe() {
  const location = useLocation();
  return <p data-testid="payment-state">{JSON.stringify(location.state)}</p>;
}

function renderPage() {
  const context = { basket, itemCount: basket.itemCount, loading: false, error: null, refresh: vi.fn() };
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
}

async function fillAddress(user) {
  await user.type(screen.getByLabelText('Full name *'), 'Asha Verma');
  await user.type(screen.getByLabelText('Phone *'), '9876543210');
  await user.type(screen.getByLabelText('Address line 1 *'), '12 Lake View Road');
  await user.type(screen.getByLabelText('City *'), 'Bengaluru');
  await user.selectOptions(screen.getByLabelText('State *'), 'Karnataka');
  await user.type(screen.getByLabelText('PIN code *'), '560001');
}

const payable = () => within(screen.getByRole('region', { name: 'Order summary' })).getByText('Payable total').nextSibling;

beforeEach(() => {
  vi.clearAllMocks();
  addressApi.listStates.mockResolvedValue(['Karnataka', 'Kerala']);
  addressApi.listAddresses.mockResolvedValue([]);
  rewardApi.getRewardAccount.mockResolvedValue({ balance: 1000 });
  orderApi.quoteCheckout.mockResolvedValue({ ...checkoutQuote, pointsToRedeem: 0, payableTotal: '1090.00' });
});

describe('CheckoutPage reward points', () => {
  it('shows the balance, the maximum redeemable points and the input below the order summary (FR-CHK-07, FR-RWD-01)', async () => {
    renderPage();

    const area = within(await screen.findByRole('region', { name: 'Reward points' }));
    expect((await area.findByText('Available balance')).nextSibling).toHaveTextContent('1,000 points');
    expect(area.getByText('Maximum you can redeem').nextSibling).toHaveTextContent('545 points');
    expect(area.getByLabelText('Points to redeem')).toHaveValue('');
    expect(area.getByText(/1 point = ₹1\.00/)).toBeInTheDocument();

    const summary = screen.getByRole('region', { name: 'Order summary' });
    const points = screen.getByRole('region', { name: 'Reward points' });
    expect(summary.compareDocumentPosition(points) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(rewardApi.getRewardAccount).toHaveBeenCalledTimes(1);
  });

  it('reduces the payable total by one rupee per point as the customer types (FR-RWD-02)', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.type(await screen.findByLabelText('Points to redeem'), '100');

    const summary = within(screen.getByRole('region', { name: 'Order summary' }));
    expect(summary.getByText('Reward points').nextSibling).toHaveTextContent('−₹100.00');
    expect(payable()).toHaveTextContent('₹990.00');

    await user.clear(screen.getByLabelText('Points to redeem'));
    expect(summary.queryByText('Reward points')).not.toBeInTheDocument();
    expect(payable()).toHaveTextContent('₹1,090.00');
  });

  it('accepts exactly the maximum and leaves an amount to pay (FR-RWD-03)', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.type(await screen.findByLabelText('Points to redeem'), '545');

    expect(screen.queryByText(/You can redeem at most/)).not.toBeInTheDocument();
    expect(payable()).toHaveTextContent('₹545.00');
    expect(screen.getByRole('button', { name: 'Pay Now' })).toBeEnabled();
  });

  it('rejects one point more than the maximum with a message and blocks Pay Now (FR-RWD-03)', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.type(await screen.findByLabelText('Points to redeem'), '546');

    expect(screen.getByText('You can redeem at most 545 points on this order.')).toBeInTheDocument();
    expect(screen.getByLabelText('Points to redeem')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('button', { name: 'Pay Now' })).toBeDisabled();
    // The summary does not apply a number that is not allowed.
    expect(payable()).toHaveTextContent('₹1,090.00');

    await user.clear(screen.getByLabelText('Points to redeem'));
    await user.type(screen.getByLabelText('Points to redeem'), '545');
    expect(screen.queryByText(/You can redeem at most/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pay Now' })).toBeEnabled();
  });

  it('allows redeeming zero and an empty box and sends no points to payment', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Points to redeem');
    await user.type(screen.getByLabelText('Points to redeem'), '0');
    expect(screen.queryByText(/You can redeem at most|whole number/)).not.toBeInTheDocument();
    await fillAddress(user);
    await user.click(screen.getByRole('button', { name: 'Pay Now' }));

    const state = JSON.parse((await screen.findByTestId('payment-state')).textContent);
    expect(state.pointsToRedeem).toBe(0);
    expect(state.payableTotal).toBe('1090.00');
  });

  it('explains text that is not a whole number and blocks Pay Now', async () => {
    const user = userEvent.setup();
    renderPage();
    const input = await screen.findByLabelText('Points to redeem');

    for (const text of ['12.5', '-3', 'abc', '1e3', '10 0']) {
      await user.clear(input);
      await user.type(input, text);
      expect(screen.getByText('Enter a whole number of points, for example 100.')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Pay Now' })).toBeDisabled();
    }
  });

  it('moves on to payment with the chosen points and the reduced amount (FR-RWD-02, FR-CHK-08)', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.type(await screen.findByLabelText('Points to redeem'), '250');
    await fillAddress(user);
    await user.click(screen.getByRole('button', { name: 'Pay Now' }));

    const state = JSON.parse((await screen.findByTestId('payment-state')).textContent);
    expect(state.pointsToRedeem).toBe(250);
    expect(state.payableTotal).toBe('840.00');
    expect(state.address.fullName).toBe('Asha Verma');
  });

  it('does not move on while the points are invalid, even with a valid address', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.type(await screen.findByLabelText('Points to redeem'), '9999');
    await fillAddress(user);
    await user.click(screen.getByRole('button', { name: 'Pay Now' }));

    expect(screen.queryByTestId('payment-state')).not.toBeInTheDocument();
  });

  it('uses the balance as the maximum when it is lower than half the total', async () => {
    orderApi.quoteCheckout.mockResolvedValue({ ...checkoutQuote, balance: 120, maxRedeemablePoints: 120, pointsToRedeem: 0 });
    rewardApi.getRewardAccount.mockResolvedValue({ balance: 120 });
    const user = userEvent.setup();
    renderPage();
    const area = within(await screen.findByRole('region', { name: 'Reward points' }));
    await screen.findByLabelText('Points to redeem');

    expect(area.getByText('Maximum you can redeem').nextSibling).toHaveTextContent('120 points');
    await user.type(screen.getByLabelText('Points to redeem'), '121');
    expect(screen.getByText('You can redeem at most 120 points on this order.')).toBeInTheDocument();
  });

  it('shows redemption as unavailable with an explanation when the balance is zero (FR-RWD-05)', async () => {
    rewardApi.getRewardAccount.mockResolvedValue({ balance: 0 });
    orderApi.quoteCheckout.mockResolvedValue({ ...checkoutQuote, balance: 0, maxRedeemablePoints: 0, pointsToRedeem: 0 });
    renderPage();

    const area = within(await screen.findByRole('region', { name: 'Reward points' }));
    expect(await area.findByText('You have no reward points to redeem.')).toBeInTheDocument();
    expect(area.queryByLabelText('Points to redeem')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pay Now' })).toBeEnabled();
  });

  it('explains when the order is too small for any points', async () => {
    orderApi.quoteCheckout.mockResolvedValue({ ...checkoutQuote, maxRedeemablePoints: 0, pointsToRedeem: 0 });
    renderPage();

    const area = within(await screen.findByRole('region', { name: 'Reward points' }));
    expect(await area.findByText('Reward points cannot be used on this order.')).toBeInTheDocument();
    expect(area.queryByLabelText('Points to redeem')).not.toBeInTheDocument();
  });

  it('keeps checkout usable when the balance cannot be loaded', async () => {
    rewardApi.getRewardAccount.mockRejectedValue({ status: 500, code: 'INTERNAL_ERROR', message: 'x', fieldErrors: [] });
    renderPage();

    expect(await screen.findByText('Your reward points could not be loaded.')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Pay Now' })).toBeEnabled());
  });

  it('never asks the server to earn, add or credit points: there is only a balance read', () => {
    expect(Object.keys(rewardApi)).toEqual(['getRewardAccount']);
  });
});

describe('checkPoints', () => {
  it('treats empty text as no redemption', () => {
    expect(checkPoints('', 545)).toEqual({ points: 0, error: null });
    expect(checkPoints('   ', 545)).toEqual({ points: 0, error: null });
  });

  it('accepts whole numbers from zero up to the maximum', () => {
    expect(checkPoints('0', 545)).toEqual({ points: 0, error: null });
    expect(checkPoints('1', 545)).toEqual({ points: 1, error: null });
    expect(checkPoints(' 545 ', 545)).toEqual({ points: 545, error: null });
  });

  it('rejects more than the maximum and names the maximum, in the singular for one point', () => {
    expect(checkPoints('546', 545).error).toBe('You can redeem at most 545 points on this order.');
    expect(checkPoints('2', 1).error).toBe('You can redeem at most 1 point on this order.');
    expect(checkPoints('1', 0).error).toBe('You can redeem at most 0 points on this order.');
    expect(checkPoints('99999999999999999999', 545).error).toContain('at most 545');
  });

  it('rejects anything that is not a plain whole number', () => {
    for (const text of ['1.5', '-1', '+1', '1,000', 'x', '1e2', '0x10']) {
      expect(checkPoints(text, 545).error, text).toBe('Enter a whole number of points, for example 100.');
    }
  });
});

describe('subtractPoints', () => {
  it('takes one rupee off per point without floating-point arithmetic', () => {
    expect(subtractPoints('1090.00', 100)).toBe('990.00');
    expect(subtractPoints('1090.00', 545)).toBe('545.00');
    expect(subtractPoints('563.95', 281)).toBe('282.95');
    expect(subtractPoints('103.95', 0)).toBe('103.95');
    expect(subtractPoints('0.10', 0)).toBe('0.10');
  });

  it('never goes below zero and ignores a negative or fractional number of points', () => {
    expect(subtractPoints('10.00', 50)).toBe('0.00');
    expect(subtractPoints('10.00', -5)).toBe('10.00');
    expect(subtractPoints('10.00', 2.9)).toBe('8.00');
  });

  it('returns an unusable total unchanged', () => {
    expect(subtractPoints('abc', 5)).toBe('abc');
    expect(subtractPoints(undefined, 5)).toBe(undefined);
  });
});
