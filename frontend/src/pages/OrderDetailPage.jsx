import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getOrder } from '../api/orderApi.js';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import Spinner from '../components/common/Spinner.jsx';
import PageShell from '../components/layout/PageShell.jsx';
import BuyAgainNotice from '../components/order/BuyAgainNotice.jsx';
import CancelOrderDialog from '../components/order/CancelOrderDialog.jsx';
import OrderItemList from '../components/order/OrderItemList.jsx';
import Button from '../components/common/Button.jsx';
import useAsync from '../hooks/useAsync.js';
import useBuyAgain from '../hooks/useBuyAgain.js';
import { formatOrderDate } from '../utils/date.js';
import { formatMoney } from '../utils/money.js';
import { orderStatusLabel, paymentMethodLabel, paymentStatusLabel } from '../utils/orderLabels.js';
import './ConfirmationPages.css';
import './OrdersPage.css';

const VALID_ID = /^[1-9]\d{0,17}$/;

function Panels({ order }) {
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
            <dt>Total</dt>
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

function CancellationInfo({ order }) {
  if (order.status === 'CANCELLED') {
    return <p className="order-detail__muted">This order was cancelled and cannot be changed.</p>;
  }
  if (order.cancellable) {
    return (
      <p className="order-detail__muted">
        You can cancel this order until {formatOrderDate(order.cancellableUntil)}.
      </p>
    );
  }
  return (
    <p className="order-detail__muted">This order can no longer be cancelled because 48 hours have passed.</p>
  );
}

/**
 * Order details (/orders/:id, SCR-11, FR-ORD-02, FR-ORD-04..06): the placed order exactly as it was placed (books, prices,
 * address, payment). "Cancel order" appears only while the server says the order is cancellable, and asks for
 * confirmation first; afterwards the page shows the cancelled order. Buy Again adds the books to the basket and reports
 * what could not be added.
 */
export default function OrderDetailPage() {
  const { id } = useParams();
  const valid = VALID_ID.test(id ?? '');
  const loaded = useAsync((signal) => (valid ? getOrder(id, signal) : Promise.resolve(null)), [id]);
  const buyAgain = useBuyAgain();
  const [confirming, setConfirming] = useState(false);
  const [cancelledOrder, setCancelledOrder] = useState(null);

  // The cancelled order returned by the server replaces what was loaded, so every field shows its new value.
  const order = cancelledOrder && cancelledOrder.id === loaded.data?.id ? cancelledOrder : loaded.data;

  function cancelled(next) {
    setConfirming(false);
    setCancelledOrder(next);
  }

  function rejected() {
    setCancelledOrder(null);
    loaded.retry();
  }

  let content;
  if (!valid || loaded.error?.status === 404) {
    content = (
      <EmptyState title="Order not found" message="We could not find this order.">
        <Link to="/orders">Back to My Orders</Link>
      </EmptyState>
    );
  } else if (loaded.error) {
    content = <ErrorMessage message="Your order could not be loaded." onRetry={loaded.retry} />;
  } else if (!order) {
    content = <Spinner label="Loading your order" />;
  } else {
    content = (
      <section className="order-detail" aria-label="Order details">
        <div className="order-detail__header">
          <div>
            <h2>
              Order <strong>{order.reference}</strong>
            </h2>
            <p className="order-detail__muted">Placed on {formatOrderDate(order.placedAt)}</p>
          </div>
          <p className={`order-card__status${order.status === 'CANCELLED' ? ' order-card__status--cancelled' : ''}`}>
            {orderStatusLabel(order.status)}
          </p>
        </div>

        {cancelledOrder ? (
          <p className="orders__notice" role="status">
            Order {order.reference} was cancelled.
          </p>
        ) : null}
        <CancellationInfo order={order} />

        <OrderItemList items={order.items} />
        <Panels order={order} />

        <div className="order-detail__actions">
          <Button disabled={buyAgain.busyId === order.id} onClick={() => buyAgain.run(order)}>
            {buyAgain.busyId === order.id ? 'Adding…' : 'Buy Again'}
          </Button>
          {order.cancellable ? (
            <Button variant="secondary" onClick={() => setConfirming(true)}>
              Cancel order
            </Button>
          ) : null}
          <Link to="/orders" className="btn btn--secondary">
            Back to My Orders
          </Link>
        </div>
      </section>
    );
  }

  return (
    <PageShell>
      <h1 className="order-detail__heading">Order details</h1>
      {buyAgain.result ? <BuyAgainNotice result={buyAgain.result} onDismiss={buyAgain.dismiss} /> : null}
      {buyAgain.error ? <ErrorMessage message={buyAgain.error} /> : null}
      {content}
      {confirming && order ? (
        <CancelOrderDialog
          order={order}
          onCancelled={cancelled}
          onRejected={rejected}
          onClose={() => setConfirming(false)}
        />
      ) : null}
    </PageShell>
  );
}
