import { Link, Navigate, useParams } from 'react-router-dom';
import { getOrder } from '../api/orderApi.js';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import Spinner from '../components/common/Spinner.jsx';
import PageShell from '../components/layout/PageShell.jsx';
import useAsync from '../hooks/useAsync.js';
import { formatMoney } from '../utils/money.js';
import { paymentMethodLabel, paymentStatusLabel } from '../utils/orderLabels.js';
import './ConfirmationPages.css';

const VALID_ID = /^[1-9]\d{0,17}$/;

/**
 * Payment confirmation (/payment/confirmed/:orderId, SCR-12, FR-CNF-03): the first of the two confirmation steps,
 * shown after a successful card, debit or UPI payment. It only reads the order, so refreshing or going back can never
 * create another order (FR-CNF-06). A Cash on Delivery order has no payment to confirm and goes straight to the
 * purchase confirmation.
 */
export default function PaymentConfirmedPage() {
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
    content = <ErrorMessage message="Your payment confirmation could not be loaded." onRetry={order.retry} />;
  } else if (!order.data) {
    content = <Spinner label="Loading your payment confirmation" />;
  } else if (order.data.paymentMethod === 'COD') {
    return <Navigate to={`/purchase/confirmed/${order.data.id}`} replace />;
  } else {
    const { data } = order;
    content = (
      <section className="confirmation" aria-label="Payment confirmation">
        <p className="confirmation__tick" aria-hidden="true">
          ✓
        </p>
        <h2 className="confirmation__title">Payment successful</h2>
        <p>
          We received {formatMoney(data.payableTotal)} by {paymentMethodLabel(data.paymentMethod)}. Payment status:{' '}
          <strong>{paymentStatusLabel(data.paymentStatus)}</strong>.
        </p>
        <Link to={`/purchase/confirmed/${data.id}`} className="btn btn--primary">
          Continue
        </Link>
      </section>
    );
  }

  return (
    <PageShell>
      <h1 className="confirmation__heading">Payment confirmed</h1>
      {content}
    </PageShell>
  );
}
