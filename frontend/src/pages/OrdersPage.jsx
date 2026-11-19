import { useState } from 'react';
import { Link } from 'react-router-dom';
import { listOrders } from '../api/orderApi.js';
import Button from '../components/common/Button.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import Spinner from '../components/common/Spinner.jsx';
import PageShell from '../components/layout/PageShell.jsx';
import BuyAgainNotice from '../components/order/BuyAgainNotice.jsx';
import CancelOrderDialog from '../components/order/CancelOrderDialog.jsx';
import OrderItemList from '../components/order/OrderItemList.jsx';
import useAsync from '../hooks/useAsync.js';
import useBuyAgain from '../hooks/useBuyAgain.js';
import { formatOrderDate, isWithinCancellationWindow } from '../utils/date.js';
import { formatMoney } from '../utils/money.js';
import { orderStatusLabel } from '../utils/orderLabels.js';
import './OrdersPage.css';

function OrderCard({ order, buyAgainBusy, onBuyAgain, onCancel }) {
  const cancelled = order.status === 'CANCELLED';
  // The list has no `cancellable` flag, so the 48-hour rule is applied to the placement time; the server still decides.
  const canCancel = order.status === 'PLACED' && isWithinCancellationWindow(order.placedAt);

  return (
    <article className="order-card" aria-label={`Order ${order.reference}`}>
      <header className="order-card__header">
        <div>
          <h2 className="order-card__reference">
            <Link to={`/orders/${order.id}`}>{order.reference}</Link>
          </h2>
          <p className="order-card__date">Placed on {formatOrderDate(order.placedAt)}</p>
        </div>
        <p className={`order-card__status${cancelled ? ' order-card__status--cancelled' : ''}`}>
          {orderStatusLabel(order.status)}
        </p>
      </header>

      <OrderItemList items={order.items} />

      <footer className="order-card__footer">
        <p className="order-card__total">
          Total <strong>{formatMoney(order.payableTotal)}</strong>
        </p>
        <div className="order-card__actions">
          <Button disabled={buyAgainBusy} onClick={() => onBuyAgain(order)}>
            {buyAgainBusy ? 'Adding…' : 'Buy Again'}
          </Button>
          {canCancel ? (
            <Button variant="secondary" onClick={() => onCancel(order)}>
              Cancel order
            </Button>
          ) : null}
        </div>
      </footer>
    </article>
  );
}

/**
 * My Orders (/orders, SCR-10, FR-ORD-01..03): the customer's own orders, newest first, each with its reference, date,
 * status (Placed or Cancelled), books and total. Buy Again puts the books back in the basket at today's prices and
 * lists what could not be added; "Cancel order" is offered only for a placed order under 48 hours old, after a
 * confirmation.
 */
export default function OrdersPage() {
  const orders = useAsync((signal) => listOrders(signal), []);
  const buyAgain = useBuyAgain();
  const [cancelling, setCancelling] = useState(null);
  const [cancelledReference, setCancelledReference] = useState(null);

  function closeDialog() {
    setCancelling(null);
  }

  function cancelled(order) {
    setCancelling(null);
    setCancelledReference(order.reference);
    orders.retry();
  }

  let content;
  if (orders.error) {
    content = <ErrorMessage message="Your orders could not be loaded." onRetry={orders.retry} />;
  } else if (!orders.data) {
    content = <Spinner label="Loading your orders" />;
  } else if (orders.data.length === 0) {
    content = (
      <EmptyState title="You have no orders yet" message="Orders you place will be listed here.">
        <Link to="/">Start shopping</Link>
      </EmptyState>
    );
  } else {
    content = (
      <ul className="orders__list" aria-busy={orders.loading}>
        {orders.data.map((order) => (
          <li key={order.id}>
            <OrderCard
              order={order}
              buyAgainBusy={buyAgain.busyId === order.id}
              onBuyAgain={buyAgain.run}
              onCancel={setCancelling}
            />
          </li>
        ))}
      </ul>
    );
  }

  return (
    <PageShell>
      <h1 className="orders__heading">My Orders</h1>
      {buyAgain.result ? <BuyAgainNotice result={buyAgain.result} onDismiss={buyAgain.dismiss} /> : null}
      {buyAgain.error ? <ErrorMessage message={buyAgain.error} /> : null}
      {cancelledReference ? (
        <p className="orders__notice" role="status">
          Order {cancelledReference} was cancelled.
        </p>
      ) : null}
      {content}
      {cancelling ? (
        <CancelOrderDialog
          order={cancelling}
          onCancelled={cancelled}
          onRejected={orders.retry}
          onClose={closeDialog}
        />
      ) : null}
    </PageShell>
  );
}
