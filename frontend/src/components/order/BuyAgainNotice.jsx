import { Link } from 'react-router-dom';
import Button from '../common/Button.jsx';
import './BuyAgainNotice.css';

const REASON_TEXT = {
  UNAVAILABLE: 'is out of stock and was not added',
  NOT_IN_CATALOGUE: 'is no longer in the catalogue and was not added',
  QUANTITY_REDUCED: 'was added with a lower quantity',
};

function describe(adjustment) {
  const text = REASON_TEXT[adjustment.reason] ?? 'was changed';
  return adjustment.reason === 'QUANTITY_REDUCED'
    ? `${adjustment.title} ${text}: you ordered ${adjustment.requestedQuantity} and ${adjustment.addedQuantity} could be added.`
    : `${adjustment.title} ${text}.`;
}

/** The result of Buy Again: everything was added, or the books that were skipped or reduced and why (FR-ORD-03). */
export default function BuyAgainNotice({ result, onDismiss }) {
  const { reference, adjustments } = result;
  return (
    <section className="buy-again-notice" role="status" aria-label="Buy Again result">
      {adjustments.length === 0 ? (
        <p className="buy-again-notice__title">The books from order {reference} were added to your basket.</p>
      ) : (
        <>
          <p className="buy-again-notice__title">
            Your basket was updated from order {reference}, with these changes:
          </p>
          <ul className="buy-again-notice__list">
            {adjustments.map((adjustment) => (
              <li key={`${adjustment.bookId}-${adjustment.reason}`}>{describe(adjustment)}</li>
            ))}
          </ul>
        </>
      )}
      <div className="buy-again-notice__actions">
        <Link to="/basket" className="btn btn--primary">
          View basket
        </Link>
        <Button variant="secondary" onClick={onDismiss}>
          Dismiss
        </Button>
      </div>
    </section>
  );
}
