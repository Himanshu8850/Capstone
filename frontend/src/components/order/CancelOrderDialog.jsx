import { cancelOrder } from '../../api/orderApi.js';
import { useFormSubmit } from '../../hooks/useFormSubmit.js';
import Button from '../common/Button.jsx';
import ErrorMessage from '../common/ErrorMessage.jsx';
import Modal from '../common/Modal.jsx';
import './CancelOrderDialog.css';

/**
 * Asks the customer to confirm cancelling an order (FR-ORD-04..06). The server decides: on success `onCancelled` gets
 * the cancelled order; when the order can no longer be cancelled (window passed, already cancelled) the server's message
 * is shown, nothing has changed, and `onRejected` lets the page reload what it shows.
 */
export default function CancelOrderDialog({ order, onCancelled, onRejected, onClose }) {
  const { submitting, formError, submit } = useFormSubmit();

  async function confirm() {
    let cancelled;
    const ok = await submit(async () => {
      try {
        cancelled = await cancelOrder(order.id);
      } catch (error) {
        if (error?.status === 409 && onRejected) onRejected();
        throw error;
      }
    });
    if (ok) onCancelled(cancelled);
  }

  return (
    <Modal title="Cancel order" onClose={onClose}>
      <p>
        Cancel order <strong>{order.reference}</strong>? The books go back to stock, any reward points you used are
        returned to your balance and the payment is refunded or cancelled. This cannot be undone.
      </p>
      {formError ? <ErrorMessage message={formError} /> : null}
      <div className="cancel-dialog__actions">
        <Button variant="secondary" onClick={onClose} disabled={submitting}>
          Keep order
        </Button>
        <Button onClick={confirm} disabled={submitting}>
          {submitting ? 'Cancelling…' : 'Yes, cancel order'}
        </Button>
      </div>
    </Modal>
  );
}
