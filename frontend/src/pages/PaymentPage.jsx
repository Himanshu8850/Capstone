import { useRef, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { placeOrder } from '../api/orderApi.js';
import Button from '../components/common/Button.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import Field from '../components/common/Field.jsx';
import PageShell from '../components/layout/PageShell.jsx';
import useBasket from '../hooks/useBasket.js';
import { formatMoney } from '../utils/money.js';
import { paymentMethodLabel } from '../utils/orderLabels.js';
import { newIdempotencyKey } from '../utils/uuid.js';
import { validateCardNumber, validateCvv, validateExpiry, validateNameOnCard, validateUpiId } from '../utils/validators.js';
import './PaymentPage.css';

const METHODS = ['CREDIT_CARD', 'DEBIT_CARD', 'UPI', 'COD'];
const EMPTY_CARD = { number: '', nameOnCard: '', cvv: '', expiry: '' };

/** Server field names such as `payment.card.expiry` map to the form field `expiry`. */
function lastSegment(field) {
  return String(field).split('.').pop();
}

function validateMethod(method, card, upiId) {
  if (method === 'CREDIT_CARD' || method === 'DEBIT_CARD') {
    const errors = {
      number: validateCardNumber(card.number),
      nameOnCard: validateNameOnCard(card.nameOnCard),
      cvv: validateCvv(card.cvv),
      expiry: validateExpiry(card.expiry),
    };
    return Object.fromEntries(Object.entries(errors).filter(([, message]) => message));
  }
  if (method === 'UPI') {
    const message = validateUpiId(upiId);
    return message ? { upiId: message } : {};
  }
  return {};
}

function paymentFor(method, card, upiId) {
  if (method === 'CREDIT_CARD' || method === 'DEBIT_CARD') {
    return {
      card: { number: card.number, nameOnCard: card.nameOnCard.trim(), cvv: card.cvv, expiry: card.expiry },
    };
  }
  if (method === 'UPI') return { upi: { upiId: upiId.trim() } };
  return undefined;
}

/**
 * Payment (/payment, SCR-08, FR-PAY-01..13): reached from checkout with the address and the amount in the route state.
 * The four methods are offered; card and UPI details are validated here (PL-06) and again by the server. Paying is
 * one `placeOrder` call with one Idempotency-Key per attempt: after a decline the key is replaced so a retry is a new
 * attempt, but after a network failure it is kept so a lost answer can never create a second order. Card details live
 * only in this component's state: they are never written to web storage and are cleared when the payment fails.
 */
export default function PaymentPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { refresh } = useBasket();
  const checkout = location.state;

  const [method, setMethod] = useState('CREDIT_CARD');
  const [card, setCard] = useState(EMPTY_CARD);
  const [upiId, setUpiId] = useState('');
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const inFlight = useRef(false);
  const idempotencyKey = useRef(null);

  // The address is absent for a basket of e-books only (AMD-004); the payable total is always set by checkout.
  if (!checkout?.payableTotal) {
    return <Navigate to="/checkout" replace />;
  }

  function chooseMethod(event) {
    setMethod(event.target.value);
    setErrors({});
    setMessage('');
  }

  function changeCard(event) {
    const { name, value } = event.target;
    setCard((current) => ({ ...current, [name]: value }));
    setErrors((current) => ({ ...current, [name]: undefined }));
  }

  async function pay(event) {
    event.preventDefault();
    if (inFlight.current) return;
    const found = validateMethod(method, card, upiId);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    inFlight.current = true;
    setSubmitting(true);
    setErrors({});
    setMessage('');
    if (idempotencyKey.current === null) idempotencyKey.current = newIdempotencyKey();
    try {
      const payment = paymentFor(method, card, upiId);
      const order = await placeOrder(
        {
          ...(checkout.address ? { address: checkout.address } : {}),
          paymentMethod: method,
          ...(payment ? { payment } : {}),
          pointsToRedeem: checkout.pointsToRedeem ?? 0,
          expectedPayableTotal: checkout.payableTotal,
        },
        idempotencyKey.current,
      );
      idempotencyKey.current = null;
      setCard(EMPTY_CARD);
      refresh();
      navigate(method === 'COD' ? `/purchase/confirmed/${order.id}` : `/payment/confirmed/${order.id}`, {
        replace: true,
      });
    } catch (caught) {
      // A network failure keeps the key (the order may exist); any answer from the server ends this attempt.
      if (caught?.code !== 'NETWORK_ERROR') idempotencyKey.current = null;
      setCard(EMPTY_CARD);
      setUpiId('');
      const fieldErrors = Object.fromEntries(
        (caught?.fieldErrors ?? []).map((item) => [lastSegment(item.field), item.message]),
      );
      setErrors(fieldErrors);
      setMessage(caught?.message || 'Something went wrong. Please try again.');
      if (caught?.code === 'BASKET_CHANGED' || caught?.code === 'OUT_OF_STOCK' || caught?.code === 'EMPTY_BASKET') {
        refresh();
      }
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  }

  return (
    <PageShell>
      <div className="payment">
        <h1 className="payment__heading">Complete Payment</h1>
        <p className="payment__amount">
          Amount payable: <strong>{formatMoney(checkout.payableTotal)}</strong>
        </p>

        <form onSubmit={pay} noValidate aria-label="Payment">
          <fieldset className="payment__methods" disabled={submitting}>
            <legend>Payment method</legend>
            {METHODS.map((value) => (
              <label key={value} className={`payment__method${method === value ? ' payment__method--selected' : ''}`}>
                <input type="radio" name="paymentMethod" value={value} checked={method === value} onChange={chooseMethod} />
                {paymentMethodLabel(value)}
              </label>
            ))}
          </fieldset>

          {method === 'CREDIT_CARD' || method === 'DEBIT_CARD' ? (
            <div className="payment__details">
              <p className="field__required-note">* required</p>
              <Field
                label="Card number"
                name="number"
                required
                inputMode="numeric"
                autoComplete="off"
                placeholder="1234-5678-9012-3456"
                hint="Format XXXX-XXXX-XXXX-XXXX."
                value={card.number}
                onChange={changeCard}
                error={errors.number}
              />
              <Field
                label="Name on card"
                name="nameOnCard"
                required
                autoComplete="off"
                value={card.nameOnCard}
                onChange={changeCard}
                error={errors.nameOnCard}
              />
              <Field
                label="CVV"
                name="cvv"
                required
                type="password"
                inputMode="numeric"
                autoComplete="off"
                hint="3 digits."
                value={card.cvv}
                onChange={changeCard}
                error={errors.cvv}
              />
              <Field
                label="Expiry (MM/YYYY)"
                name="expiry"
                required
                autoComplete="off"
                placeholder="MM/YYYY"
                value={card.expiry}
                onChange={changeCard}
                error={errors.expiry}
              />
            </div>
          ) : null}

          {method === 'UPI' ? (
            <div className="payment__details">
              <p className="field__required-note">* required</p>
              <Field
                label="UPI ID"
                name="upiId"
                required
                autoComplete="off"
                placeholder="name@bank"
                value={upiId}
                onChange={(event) => {
                  setUpiId(event.target.value);
                  setErrors((current) => ({ ...current, upiId: undefined }));
                }}
                error={errors.upiId}
              />
            </div>
          ) : null}

          {method === 'COD' ? (
            <p className="payment__details payment__cod">
              Pay in cash when your order is delivered. No payment details are needed now.
            </p>
          ) : null}

          {message ? <ErrorMessage message={message} /> : null}
          {message ? (
            <p className="payment__links">
              <Link to="/basket">Review your basket</Link> · <Link to="/checkout">Back to checkout</Link>
            </p>
          ) : null}

          <div className="payment__actions">
            <Button type="submit" disabled={submitting}>
              {submitting ? 'Processing…' : 'Pay Now'}
            </Button>
          </div>
        </form>
      </div>
    </PageShell>
  );
}
