import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/orderApi.js', () => ({ placeOrder: vi.fn() }));

import { placeOrder } from '../../api/orderApi.js';
import { AuthContext } from '../../context/AuthContext.jsx';
import { BasketContext } from '../../context/BasketContext.jsx';
import { apiErrors, basket, order } from '../../test/fixtures/index.js';
import PaymentPage from '../PaymentPage.jsx';

const CHECKOUT_STATE = {
  address: {
    fullName: 'Asha Verma',
    phone: '9876543210',
    addressLine1: '12 Lake View Road',
    city: 'Bengaluru',
    state: 'Karnataka',
    pinCode: '560001',
    country: 'India',
  },
  pointsToRedeem: 100,
  payableTotal: '990.00',
};

function Where() {
  const location = useLocation();
  return <p data-testid="where">{location.pathname}</p>;
}

function renderPage(state = CHECKOUT_STATE) {
  const refresh = vi.fn();
  render(
    <MemoryRouter initialEntries={[{ pathname: '/payment', state }]}>
      <AuthContext.Provider value={{ customer: { id: 1 }, loading: false, login: vi.fn(), logout: vi.fn() }}>
        <BasketContext.Provider value={{ basket, itemCount: basket.itemCount, loading: false, error: null, refresh }}>
          <Routes>
            <Route path="/payment" element={<PaymentPage />} />
            <Route path="*" element={<Where />} />
          </Routes>
        </BasketContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
  return { refresh };
}

async function fillCard(user, overrides = {}) {
  const card = { number: '4111-1111-1111-1111', name: 'Asha Verma', cvv: '123', expiry: '12/2099', ...overrides };
  await user.type(screen.getByLabelText('Card number *'), card.number);
  await user.type(screen.getByLabelText('Name on card *'), card.name);
  await user.type(screen.getByLabelText('CVV *'), card.cvv);
  await user.type(screen.getByLabelText('Expiry (MM/YYYY) *'), card.expiry);
}

const pay = (user) => user.click(screen.getByRole('button', { name: 'Pay Now' }));

beforeEach(() => {
  vi.clearAllMocks();
});

describe('PaymentPage', () => {
  it('shows the title, the payable amount and exactly the four methods (FR-PAY-01, FR-PAY-03)', () => {
    renderPage();

    expect(screen.getByRole('heading', { name: 'Complete Payment' })).toBeInTheDocument();
    expect(screen.getByText('₹990.00')).toBeInTheDocument();
    const methods = screen.getAllByRole('radio').map((radio) => radio.parentElement.textContent);
    expect(methods).toEqual(['Credit Card', 'Debit Card', 'UPI', 'Cash on Delivery']);
    expect(screen.getByRole('button', { name: 'Pay Now' })).toBeEnabled();
  });

  it('sends a visitor who did not come from checkout back to checkout', () => {
    renderPage(null);
    expect(screen.getByTestId('where')).toHaveTextContent('/checkout');
  });

  it('places an e-book only order without an address (AMD-004)', async () => {
    placeOrder.mockResolvedValue({ ...order, address: null });
    const user = userEvent.setup();
    renderPage({ pointsToRedeem: 0, payableTotal: '313.95' });

    await fillCard(user);
    await pay(user);

    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/payment/confirmed/2'));
    expect(placeOrder.mock.calls[0][0]).not.toHaveProperty('address');
  });

  it('shows the form of the selected method only (FR-PAY-03)', async () => {
    const user = userEvent.setup();
    renderPage();
    expect(screen.getByLabelText('Card number *')).toBeInTheDocument();

    await user.click(screen.getByLabelText('UPI'));
    expect(screen.getByLabelText('UPI ID *')).toBeInTheDocument();
    expect(screen.queryByLabelText('Card number *')).not.toBeInTheDocument();

    await user.click(screen.getByLabelText('Cash on Delivery'));
    expect(screen.getByText(/No payment details are needed now/)).toBeInTheDocument();
    expect(screen.queryByLabelText('UPI ID *')).not.toBeInTheDocument();
  });

  describe('card validation (PL-06, FR-PAY-04)', () => {
    it('shows an inline error for each invalid field and does not call the API', async () => {
      const user = userEvent.setup();
      renderPage();

      await fillCard(user, { number: '4111111111111111', name: 'A', cvv: '12', expiry: '13/2099' });
      await pay(user);

      expect(screen.getByText('Card number must look like 1234-5678-9012-3456.')).toBeInTheDocument();
      expect(screen.getByText('Name on card must be 2 to 60 characters.')).toBeInTheDocument();
      expect(screen.getByText('CVV must be exactly 3 digits.')).toBeInTheDocument();
      expect(screen.getByText('Expiry must be in the form MM/YYYY.')).toBeInTheDocument();
      expect(placeOrder).not.toHaveBeenCalled();
    });

    it('rejects an expired card', async () => {
      const user = userEvent.setup();
      renderPage();

      await fillCard(user, { expiry: '01/2020' });
      await pay(user);

      expect(screen.getByText('This card has expired.')).toBeInTheDocument();
      expect(placeOrder).not.toHaveBeenCalled();
    });

    it('requires every field when the form is empty', async () => {
      const user = userEvent.setup();
      renderPage();

      await pay(user);

      expect(screen.getByText('Enter the card number.')).toBeInTheDocument();
      expect(screen.getByText('Enter the CVV.')).toBeInTheDocument();
      expect(screen.getByText('Enter the expiry date.')).toBeInTheDocument();
    });
  });

  describe('UPI validation (FR-PAY-05)', () => {
    it('blocks a blank or malformed UPI ID', async () => {
      const user = userEvent.setup();
      renderPage();
      await user.click(screen.getByLabelText('UPI'));

      await pay(user);
      expect(screen.getByText('Enter your UPI ID.')).toBeInTheDocument();

      await user.type(screen.getByLabelText('UPI ID *'), 'not-a-upi-id');
      await pay(user);
      expect(screen.getByText('Enter a UPI ID such as name@bank.')).toBeInTheDocument();
      expect(placeOrder).not.toHaveBeenCalled();
    });
  });

  describe('successful payment', () => {
    it('places the order with the card and the quoted amount, then opens the payment confirmation (FR-CNF-03)', async () => {
      placeOrder.mockResolvedValue(order);
      const user = userEvent.setup();
      const { refresh } = renderPage();

      await fillCard(user);
      await pay(user);

      await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/payment/confirmed/2'));
      const [request, key] = placeOrder.mock.calls[0];
      expect(request).toEqual({
        address: CHECKOUT_STATE.address,
        paymentMethod: 'CREDIT_CARD',
        payment: {
          card: { number: '4111-1111-1111-1111', nameOnCard: 'Asha Verma', cvv: '123', expiry: '12/2099' },
        },
        pointsToRedeem: 100,
        expectedPayableTotal: '990.00',
      });
      expect(key).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
      expect(refresh).toHaveBeenCalled();
    });

    it('places a UPI order with the UPI ID only', async () => {
      placeOrder.mockResolvedValue({ ...order, paymentMethod: 'UPI' });
      const user = userEvent.setup();
      renderPage();

      await user.click(screen.getByLabelText('UPI'));
      await user.type(screen.getByLabelText('UPI ID *'), 'demo@upi');
      await pay(user);

      await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/payment/confirmed/2'));
      expect(placeOrder.mock.calls[0][0]).toMatchObject({
        paymentMethod: 'UPI',
        payment: { upi: { upiId: 'demo@upi' } },
      });
    });

    it('places a Cash on Delivery order without payment details and skips the payment confirmation (FR-CNF-03)', async () => {
      placeOrder.mockResolvedValue({ ...order, paymentMethod: 'COD', paymentStatus: 'PENDING_ON_DELIVERY' });
      const user = userEvent.setup();
      renderPage();

      await user.click(screen.getByLabelText('Cash on Delivery'));
      await pay(user);

      await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/purchase/confirmed/2'));
      expect(placeOrder.mock.calls[0][0]).not.toHaveProperty('payment');
      expect(placeOrder.mock.calls[0][0].paymentMethod).toBe('COD');
    });
  });

  describe('failure and retry (FR-PAY-10)', () => {
    it('shows the generic decline message, clears the card fields, keeps the page and uses a new key for the retry', async () => {
      placeOrder.mockRejectedValueOnce(apiErrors.PAYMENT_DECLINED).mockResolvedValueOnce(order);
      const user = userEvent.setup();
      renderPage();

      await fillCard(user, { number: '4000-0000-0000-0002' });
      await pay(user);

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Your payment was declined. Please try again or choose another payment method.',
      );
      expect(screen.getByLabelText('Card number *')).toHaveValue('');
      expect(screen.getByLabelText('CVV *')).toHaveValue('');
      expect(screen.getByLabelText('Name on card *')).toHaveValue('');
      expect(screen.getByLabelText('Expiry (MM/YYYY) *')).toHaveValue('');
      expect(screen.getByRole('link', { name: 'Review your basket' })).toBeInTheDocument();
      expect(screen.queryByTestId('where')).not.toBeInTheDocument();

      await fillCard(user);
      await pay(user);

      await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/payment/confirmed/2'));
      expect(placeOrder.mock.calls[1][1]).not.toBe(placeOrder.mock.calls[0][1]);
    });

    it('lets the customer switch to another method after a decline', async () => {
      placeOrder.mockRejectedValueOnce(apiErrors.PAYMENT_DECLINED);
      const user = userEvent.setup();
      renderPage();

      await fillCard(user);
      await pay(user);
      await screen.findByRole('alert');
      await user.click(screen.getByLabelText('Cash on Delivery'));

      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Pay Now' })).toBeEnabled();
    });

    it('keeps the same key after a network failure so a lost answer cannot create a second order (FR-PAY-11)', async () => {
      placeOrder
        .mockRejectedValueOnce({ status: 0, code: 'NETWORK_ERROR', message: 'We could not reach the server.', fieldErrors: [] })
        .mockResolvedValueOnce(order);
      const user = userEvent.setup();
      renderPage();

      await fillCard(user);
      await pay(user);
      await screen.findByText('We could not reach the server.');
      await fillCard(user);
      await pay(user);

      await waitFor(() => expect(placeOrder).toHaveBeenCalledTimes(2));
      expect(placeOrder.mock.calls[1][1]).toBe(placeOrder.mock.calls[0][1]);
    });

    it('shows the server message for a changed basket and refreshes the basket', async () => {
      placeOrder.mockRejectedValueOnce(apiErrors.BASKET_CHANGED);
      const user = userEvent.setup();
      const { refresh } = renderPage();

      await user.click(screen.getByLabelText('Cash on Delivery'));
      await pay(user);

      expect(await screen.findByRole('alert')).toHaveTextContent('Prices or availability in your basket have changed.');
      expect(refresh).toHaveBeenCalled();
    });

    it('shows a server field error under the right field', async () => {
      placeOrder.mockRejectedValueOnce({
        status: 400,
        code: 'VALIDATION_FAILED',
        message: 'The request contains invalid values.',
        fieldErrors: [{ field: 'payment.card.expiry', message: 'This card has expired.' }],
      });
      const user = userEvent.setup();
      renderPage();

      await fillCard(user);
      await pay(user);

      expect(await screen.findByText('This card has expired.')).toBeInTheDocument();
    });
  });

  describe('submitting', () => {
    it('disables Pay Now and the method choice while the payment is pending and sends one request (FR-PAY-11)', async () => {
      let finish;
      placeOrder.mockReturnValue(new Promise((resolve) => (finish = resolve)));
      const user = userEvent.setup();
      renderPage();

      await user.click(screen.getByLabelText('Cash on Delivery'));
      await pay(user);

      const button = await screen.findByRole('button', { name: 'Processing…' });
      expect(button).toBeDisabled();
      expect(screen.getByLabelText('UPI')).toBeDisabled();
      await user.click(button);
      expect(placeOrder).toHaveBeenCalledTimes(1);

      finish(order);
      await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/purchase/confirmed/2'));
    });
  });

  describe('card data is never kept (FR-PAY-13)', () => {
    it('writes nothing to local or session storage while typing, paying or failing', async () => {
      placeOrder.mockRejectedValueOnce(apiErrors.PAYMENT_DECLINED);
      const user = userEvent.setup();
      renderPage();

      await fillCard(user);
      expect(JSON.stringify({ ...window.localStorage })).not.toMatch(/4111|123/);
      await pay(user);
      await screen.findByRole('alert');

      expect(window.localStorage.length).toBe(0);
      expect(window.sessionStorage.length).toBe(0);
    });

    it('does not show the CVV in clear text', () => {
      renderPage();
      expect(screen.getByLabelText('CVV *')).toHaveAttribute('type', 'password');
    });
  });
});
