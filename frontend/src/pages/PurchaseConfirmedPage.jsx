import { Link, useParams } from 'react-router-dom';
import { getOrder } from '../api/orderApi.js';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import Spinner from '../components/common/Spinner.jsx';
import PageShell from '../components/layout/PageShell.jsx';
import useAsync from '../hooks/useAsync.js';
import { authorLink, formatLabel } from '../utils/bookLinks.js';
import { formatDeliveryDate, formatOrderDate } from '../utils/date.js';
import { formatMoney } from '../utils/money.js';
import { paymentMethodLabel, paymentStatusLabel } from '../utils/orderLabels.js';
import './ConfirmationPages.css';

const VALID_ID = /^[1-9]\d{0,17}$/;

function OrderItem({ item }) {
  const { book } = item;
  const delivery = formatDeliveryDate(book.deliveryDate);
  return (
    <li className="confirmation__item">
      <img src={book.frontCoverUrl} alt={`Cover of ${book.title}`} width="64" height="96" />
      <div className="confirmation__item-details">
        <h3 className="confirmation__item-title">
          <Link to={`/books/${book.id}`}>{book.title}</Link>
        </h3>
        <p>
          by <Link to={authorLink(book.author)}>{book.author}</Link>
        </p>
        <p className="confirmation__muted">{book.description}</p>
        <p className="confirmation__muted">
          {formatLabel(book.format)}
          {book.categories?.length ? ` · ${book.categories.map((category) => category.name).join(', ')}` : ''}
        </p>
        <p>
          {item.quantity} × {formatMoney(item.unitPrice)} = <strong>{formatMoney(item.lineTotal)}</strong>
        </p>
        {delivery ? <p className="confirmation__muted">Delivery by {delivery}</p> : null}
      </div>
    </li>
  );
}

function Details({ order }) {
  const { address } = order;
  return (
    <>
      <section className="confirmation__panel" aria-label="Order summary">
        <h3>Order summary</h3>
        <dl className="confirmation__totals">
          <div>
            <dt>Subtotal</dt>
            <dd>{formatMoney(order.subtotal)}</dd>
          </div>
          <div>
            <dt>Tax (5%)</dt>
            <dd>{formatMoney(order.tax)}</dd>
          </div>
          <div>
            <dt>Delivery</dt>
            <dd>{formatMoney(order.deliveryCharge)}</dd>
          </div>
          {order.pointsRedeemed > 0 ? (
            <div>
              <dt>Reward points</dt>
              <dd>−{formatMoney(`${order.pointsRedeemed}.00`)}</dd>
            </div>
          ) : null}
          <div className="confirmation__totals-total">
            <dt>Total paid or payable</dt>
            <dd>{formatMoney(order.payableTotal)}</dd>
          </div>
        </dl>
      </section>
      {address ? (
        <section className="confirmation__panel" aria-label="Delivery address">
          <h3>Delivery address</h3>
          <address className="confirmation__address">
            <strong>{address.fullName}</strong>
            <span>{address.addressLine1}</span>
            {address.addressLine2 ? <span>{address.addressLine2}</span> : null}
            <span>
              {address.city}, {address.state} {address.pinCode}
            </span>
            <span>{address.country}</span>
            <span>Phone: {address.phone}</span>
          </address>
        </section>
      ) : null}
      <section className="confirmation__panel" aria-label="Payment">
        <h3>Payment</h3>
        <p>
          {paymentMethodLabel(order.paymentMethod)} · <strong>{paymentStatusLabel(order.paymentStatus)}</strong>
        </p>
      </section>
    </>
  );
}

/**
 * Purchase confirmation (/purchase/confirmed/:orderId, SCR-09, FR-CNF-01/02/05/06): the success message, the order
 * reference, every purchased book with its details and delivery date, the totals, the delivery address and the
 * payment status. It only reads the order, so a refresh or the back button never creates another one.
 */
export default function PurchaseConfirmedPage() {
  const { orderId } = useParams();
  const valid = VALID_ID.test(orderId ?? '');
  const order = useAsync((signal) => (valid ? getOrder(orderId, signal) : Promise.resolve(null)), [orderId]);

  let content;
  if (!valid || order.error?.status === 404) {
    content = (
      <EmptyState title="Order not found" message="We could not find this order.">
        <Link to="/">Back to the catalogue</Link>
      </EmptyState>
    );
  } else if (order.error) {
    content = <ErrorMessage message="Your order could not be loaded." onRetry={order.retry} />;
  } else if (!order.data) {
    content = <Spinner label="Loading your order" />;
  } else {
    const { data } = order;
    content = (
      <section className="confirmation" aria-label="Purchase confirmation">
        <p className="confirmation__tick" aria-hidden="true">
          ✓
        </p>
        <h2 className="confirmation__title">Your purchase was successful</h2>
        <p>
          Order <strong>{data.reference}</strong> was placed on {formatOrderDate(data.placedAt)}.
        </p>
        <ul className="confirmation__items">
          {data.items.map((item) => (
            <OrderItem key={item.book.id} item={item} />
          ))}
        </ul>
        <Details order={data} />
        <Link to="/" className="btn btn--primary">
          Continue your Shopping
        </Link>
      </section>
    );
  }

  return (
    <PageShell>
      <h1 className="confirmation__heading">Purchase confirmed</h1>
      {content}
    </PageShell>
  );
}
