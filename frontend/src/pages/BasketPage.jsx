import { useState } from 'react';
import { Link } from 'react-router-dom';
import RecommendedForYou from '../components/book/RecommendedForYou.jsx';
import Button from '../components/common/Button.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import Spinner from '../components/common/Spinner.jsx';
import PageShell from '../components/layout/PageShell.jsx';
import { maxQuantityFor } from '../context/BasketContext.jsx';
import useBasket from '../hooks/useBasket.js';
import { authorLink, formatLabel } from '../utils/bookLinks.js';
import { formatDeliveryDate } from '../utils/date.js';
import { formatMoney } from '../utils/money.js';
import './BasketPage.css';

const REASON_TEXT = {
  UNAVAILABLE: 'is out of stock and was not added',
  NOT_IN_CATALOGUE: 'is no longer in the catalogue and was not added',
  QUANTITY_REDUCED: 'was added with a lower quantity',
};

function describeAdjustment(adjustment) {
  const text = REASON_TEXT[adjustment.reason] ?? 'was changed';
  return adjustment.reason === 'QUANTITY_REDUCED'
    ? `${adjustment.title} ${text}: you asked for ${adjustment.requestedQuantity} and ${adjustment.addedQuantity} could be added.`
    : `${adjustment.title} ${text}.`;
}

function MergeNotice({ report, onDismiss }) {
  return (
    <section className="basket-notice" role="status" aria-label="Basket update">
      <p className="basket-notice__title">Some items from your basket were adjusted when you signed in:</p>
      <ul className="basket-notice__list">
        {report.adjustments.map((adjustment) => (
          <li key={`${adjustment.bookId}-${adjustment.reason}`}>{describeAdjustment(adjustment)}</li>
        ))}
      </ul>
      <Button variant="secondary" onClick={onDismiss}>
        Dismiss
      </Button>
    </section>
  );
}

function BasketLine({ line, busy, onChange, onRemove }) {
  const { book, quantity } = line;
  const delivery = formatDeliveryDate(book.deliveryDate);
  const limit = maxQuantityFor(book);

  return (
    <li className="basket-line">
      <Link to={`/books/${book.id}`} className="basket-line__cover-link" tabIndex={-1} aria-hidden="true">
        <img className="basket-line__cover" src={book.frontCoverUrl} alt={`Cover of ${book.title}`} width="80" height="120" />
      </Link>

      <div className="basket-line__details">
        <h2 className="basket-line__title">
          <Link to={`/books/${book.id}`}>{book.title}</Link>
        </h2>
        <p className="basket-line__author">
          by <Link to={authorLink(book.author)}>{book.author}</Link>
        </p>
        <p className="basket-line__meta">
          {formatLabel(book.format)} · {formatMoney(book.price)} each
        </p>
        {book.available ? (
          delivery ? <p className="basket-line__delivery">Delivery by {delivery}</p> : null
        ) : (
          <p className="basket-line__unavailable">Unavailable</p>
        )}
      </div>

      <div className="basket-line__controls">
        <div className="basket-line__stepper" role="group" aria-label={`Quantity of ${book.title}`}>
          <Button
            variant="secondary"
            aria-label={`Decrease quantity of ${book.title}`}
            disabled={busy || quantity <= 1}
            onClick={() => onChange(book.id, quantity - 1)}
          >
            −
          </Button>
          <span className="basket-line__quantity" aria-label={`Quantity ${quantity}`}>
            {quantity}
          </span>
          <Button
            variant="secondary"
            aria-label={`Increase quantity of ${book.title}`}
            disabled={busy || quantity >= limit}
            onClick={() => onChange(book.id, quantity + 1)}
          >
            +
          </Button>
        </div>
        <p className="basket-line__total">{formatMoney(line.lineTotal)}</p>
        <Button variant="secondary" disabled={busy} onClick={() => onRemove(book.id)}>
          Remove<span className="visually-hidden"> {book.title}</span>
        </Button>
      </div>
    </li>
  );
}

function Totals({ basket }) {
  return (
    <section className="basket-totals" aria-label="Order totals">
      <dl className="basket-totals__list">
        <div>
          <dt>Subtotal</dt>
          <dd>{formatMoney(basket.subtotal)}</dd>
        </div>
        <div>
          <dt>Tax (5%)</dt>
          <dd>{formatMoney(basket.tax)}</dd>
        </div>
        <div>
          <dt>Delivery</dt>
          <dd>{formatMoney(basket.deliveryCharge)}</dd>
        </div>
        <div className="basket-totals__total">
          <dt>Total</dt>
          <dd>{formatMoney(basket.total)}</dd>
        </div>
      </dl>
      <Link to="/checkout" className="btn btn--primary basket-totals__checkout">
        Proceed to Checkout
      </Link>
    </section>
  );
}

/**
 * The basket (/basket, FR-BSK-01..07, SCR-06). Guests and customers can open it. Each line has a quantity stepper
 * (1 to 10, and never more than the stock of a physical book) and a remove button; the totals come from the API.
 * "Proceed to Checkout" opens /checkout, which sends a guest to the login page and brings them back (A-12).
 * A signed-in customer with order history also sees "Recommended for You", which leaves out the books in the basket.
 */
export default function BasketPage() {
  const { basket, loading, error, mergeReport, updateItem, removeItem, refresh, dismissMergeReport } = useBasket();
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState(null);

  async function run(action) {
    setBusy(true);
    setActionError(null);
    try {
      await action();
    } catch (caught) {
      setActionError(caught?.message || 'The basket could not be updated. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  const changeQuantity = (bookId, quantity) => run(() => updateItem(bookId, quantity));
  const remove = (bookId) => run(() => removeItem(bookId));

  let content;
  if (error) {
    content = <ErrorMessage message="Your basket could not be loaded." onRetry={refresh} />;
  } else if (loading && basket.items.length === 0) {
    content = <Spinner label="Loading your basket" />;
  } else if (basket.items.length === 0) {
    content = (
      <EmptyState title="Your basket is empty" message="Add a book to see it here.">
        <Link to="/">Continue shopping</Link>
      </EmptyState>
    );
  } else {
    content = (
      <div className="basket__layout" aria-busy={busy || loading}>
        <ul className="basket__lines">
          {basket.items.map((line) => (
            <BasketLine key={line.book.id} line={line} busy={busy} onChange={changeQuantity} onRemove={remove} />
          ))}
        </ul>
        <Totals basket={basket} />
      </div>
    );
  }

  return (
    <PageShell>
      <h1 className="basket__heading">Basket</h1>
      {mergeReport ? <MergeNotice report={mergeReport} onDismiss={dismissMergeReport} /> : null}
      {actionError ? <ErrorMessage message={actionError} /> : null}
      {content}
      <RecommendedForYou refreshKey={basket.items.map((line) => line.book.id).join(',')} />
    </PageShell>
  );
}
