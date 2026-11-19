import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as addressApi from '../api/addressApi.js';
import { quoteCheckout } from '../api/orderApi.js';
import AddressForm from '../components/address/AddressForm.jsx';
import Button from '../components/common/Button.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import Field from '../components/common/Field.jsx';
import Spinner from '../components/common/Spinner.jsx';
import PageShell from '../components/layout/PageShell.jsx';
import RewardsRedemption, { checkPoints } from '../components/checkout/RewardsRedemption.jsx';
import useAsync from '../hooks/useAsync.js';
import useBasket from '../hooks/useBasket.js';
import { EMPTY_ADDRESS, addressToValues, validateAddressValues, valuesToAddress } from '../utils/addressForm.js';
import { formatLabel } from '../utils/bookLinks.js';
import { formatMoney, subtractPoints } from '../utils/money.js';
import './CheckoutPage.css';

function Breadcrumb() {
  return (
    <nav className="checkout__breadcrumb" aria-label="Breadcrumb">
      <ol>
        <li>
          <Link to="/">Home</Link>
        </li>
        <li>
          <Link to="/basket">Basket</Link>
        </li>
        <li aria-current="page">Checkout</li>
      </ol>
    </nav>
  );
}

function Summary({ quote }) {
  return (
    <section className="checkout-summary" aria-label="Order summary">
      <h2 className="checkout__section-heading">Order summary</h2>
      <dl className="checkout-summary__list">
        <div>
          <dt>Subtotal</dt>
          <dd>{formatMoney(quote.subtotal)}</dd>
        </div>
        <div>
          <dt>Tax (5%)</dt>
          <dd>{formatMoney(quote.tax)}</dd>
        </div>
        <div>
          <dt>Delivery</dt>
          <dd>{formatMoney(quote.deliveryCharge)}</dd>
        </div>
        {quote.pointsToRedeem > 0 ? (
          <div>
            <dt>Reward points</dt>
            <dd>−{formatMoney(`${quote.pointsToRedeem}.00`)}</dd>
          </div>
        ) : null}
        <div className="checkout-summary__total">
          <dt>Payable total</dt>
          <dd>{formatMoney(quote.payableTotal)}</dd>
        </div>
      </dl>
    </section>
  );
}

/**
 * Checkout (/checkout, SCR-07, FR-CHK-01..05, 07..09): the basket items, the delivery address (a saved one fills the
 * form; any value can be edited), the order summary from `quoteCheckout`, the reward-point redemption below it and
 * Pay Now, which moves on to /payment only when the basket has no problem, the address is valid and the points are
 * valid. The summary shows the typed points at once (payable total = total - points); the quote is taken with no
 * points, so its `maxRedeemablePoints` is the cap for what the customer types. The login is required by the route.
 */
export default function CheckoutPage() {
  const navigate = useNavigate();
  const { basket, loading: basketLoading, error: basketError, refresh } = useBasket();
  const [values, setValues] = useState(EMPTY_ADDRESS);
  const [errors, setErrors] = useState({});
  const [savedId, setSavedId] = useState('');
  const [pointsText, setPointsText] = useState('');

  const states = useAsync(() => addressApi.listStates(), []);
  const saved = useAsync(() => addressApi.listAddresses(), []);
  const quote = useAsync(async () => {
    if (basketLoading || basket.items.length === 0) return null;
    return quoteCheckout(0);
  }, [basketLoading, basket.total, basket.itemCount]);

  function change(event) {
    const { name, value } = event.target;
    setValues((current) => ({ ...current, [name]: value }));
    setErrors((current) => ({ ...current, [name]: undefined }));
  }

  function chooseSaved(event) {
    const id = event.target.value;
    setSavedId(id);
    setErrors({});
    const address = (saved.data ?? []).find((item) => String(item.id) === id);
    setValues(address ? addressToValues(address) : EMPTY_ADDRESS);
  }

  // The points are checked against the current cap every render, so a basket change that lowers the cap is caught.
  const redemption = quote.data ? checkPoints(pointsText, quote.data.maxRedeemablePoints) : { points: 0, error: null };
  const pointsToRedeem = redemption.points;
  const payableTotal = quote.data ? subtractPoints(quote.data.total, pointsToRedeem) : '';

  // A basket of e-books only has nothing to deliver, so no address is asked for or sent (AMD-004).
  const needsAddress = basket.items.some((line) => line.book.format !== 'EBOOK');

  function payNow(event) {
    event.preventDefault();
    if (needsAddress) {
      const found = validateAddressValues(values);
      if (Object.keys(found).length > 0) {
        setErrors(found);
        return;
      }
    }
    if (redemption.error) return;
    navigate('/payment', {
      state: {
        ...(needsAddress ? { address: valuesToAddress(values) } : {}),
        pointsToRedeem,
        payableTotal,
      },
    });
  }

  if (basketError) {
    return (
      <PageShell>
        <h1 className="checkout__heading">Checkout</h1>
        <ErrorMessage message="Your basket could not be loaded." onRetry={refresh} />
      </PageShell>
    );
  }
  if (basketLoading && basket.items.length === 0) {
    return (
      <PageShell>
        <h1 className="checkout__heading">Checkout</h1>
        <Spinner label="Loading your basket" />
      </PageShell>
    );
  }
  if (basket.items.length === 0) {
    return (
      <PageShell>
        <h1 className="checkout__heading">Checkout</h1>
        <EmptyState title="Your basket is empty" message="Add a book before you check out.">
          <Link to="/">Continue shopping</Link>
        </EmptyState>
      </PageShell>
    );
  }

  const issues = quote.data?.issues ?? [];
  const blocked = issues.length > 0 || redemption.error !== null;

  return (
    <PageShell>
      <Breadcrumb />
      <h1 className="checkout__heading">Checkout</h1>
      <div className="checkout__layout">
        <div className="checkout__main">
          <section aria-label="Your basket">
            <h2 className="checkout__section-heading">Your basket</h2>
            <ul className="checkout__items">
              {basket.items.map((line) => (
                <li key={line.book.id} className="checkout__item">
                  <img src={line.book.frontCoverUrl} alt={`Cover of ${line.book.title}`} width="48" height="72" />
                  <div>
                    <p className="checkout__item-title">{line.book.title}</p>
                    <p className="checkout__item-meta">
                      {formatLabel(line.book.format)} · {line.quantity} × {formatMoney(line.book.price)}
                    </p>
                  </div>
                  <p className="checkout__item-total">{formatMoney(line.lineTotal)}</p>
                </li>
              ))}
            </ul>
            <Link to="/basket">Change basket</Link>
          </section>

          <form
            id="checkout-form"
            onSubmit={payNow}
            noValidate
            aria-label={needsAddress ? 'Delivery address' : 'Checkout'}
          >
            {needsAddress ? (
              <>
                <h2 className="checkout__section-heading">Delivery address</h2>
                {saved.data && saved.data.length === 0 ? (
                  <p className="checkout__note">
                    You have no saved addresses yet. Enter one below or{' '}
                    <Link to="/account/addresses">manage your saved addresses</Link>.
                  </p>
                ) : null}
                {saved.data && saved.data.length > 0 ? (
                  <Field label="Use a saved address" as="select" value={savedId} onChange={chooseSaved}>
                    <option value="">Enter a new address</option>
                    {saved.data.map((address) => (
                      <option key={address.id} value={address.id}>
                        {address.fullName}, {address.addressLine1}, {address.city}
                      </option>
                    ))}
                  </Field>
                ) : null}
                <AddressForm values={values} errors={errors} states={states.data ?? []} onChange={change} />
              </>
            ) : (
              <p className="checkout__note">Your e-books are delivered digitally, so no delivery address is needed.</p>
            )}
          </form>
        </div>

        <aside className="checkout__side">
          {quote.error ? (
            <ErrorMessage message={quote.error.message || 'The order summary could not be loaded.'} onRetry={quote.retry} />
          ) : !quote.data ? (
            <Spinner label="Loading the order summary" />
          ) : (
            <>
              <Summary quote={{ ...quote.data, pointsToRedeem, payableTotal }} />
              <RewardsRedemption
                maxRedeemablePoints={quote.data.maxRedeemablePoints}
                value={pointsText}
                error={redemption.error}
                onChange={setPointsText}
              />
              {issues.length > 0 ? (
                <div className="checkout__issues" role="alert">
                  <p>Your basket needs attention before you can pay:</p>
                  <ul>
                    {issues.map((issue) => (
                      <li key={`${issue.bookId}-${issue.type}`}>
                        {issue.title}: {issue.message}
                      </li>
                    ))}
                  </ul>
                  <Link to="/basket">Review your basket</Link>
                </div>
              ) : null}
              <Button type="submit" form="checkout-form" disabled={blocked}>
                Pay Now
              </Button>
            </>
          )}
        </aside>
      </div>
    </PageShell>
  );
}
