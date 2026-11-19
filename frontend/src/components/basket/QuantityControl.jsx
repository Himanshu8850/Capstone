import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { maxQuantityFor } from '../../context/BasketContext.jsx';
import useBasket from '../../hooks/useBasket.js';
import Button from '../common/Button.jsx';
import './QuantityControl.css';

/**
 * Cart control (AMD-002, T156), shared by the book card, the compact card and the book page. A book that is not in
 * the basket shows "Add to Cart". A book in the basket shows minus, the quantity, plus and a "Go to Cart" link; minus
 * at 1 removes the line, and plus stops at the limit min(10, stock). A book that cannot be bought (a physical book
 * with no stock) shows no control: the page already says Unavailable. Works for guests and customers through the
 * BasketContext. A rejected change is explained under the control.
 */
export default function QuantityControl({ book }) {
  const { addItem, updateItem, removeItem, quantityOf } = useBasket();
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState(null);
  const mounted = useRef(true);
  const quantity = quantityOf(book.id);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  if (!book.available) return null;

  const limit = maxQuantityFor(book);
  const atLimit = quantity >= limit;

  async function run(change) {
    setBusy(true);
    setMessage(null);
    try {
      await change();
    } catch (caught) {
      if (mounted.current) {
        setMessage(caught?.message || 'The basket could not be changed. Please try again.');
      }
    } finally {
      if (mounted.current) setBusy(false);
    }
  }

  return (
    <div className="quantity-control">
      {quantity === 0 ? (
        <Button onClick={() => run(() => addItem(book.id, 1))} disabled={busy}>
          Add to Cart
        </Button>
      ) : (
        <>
          <div className="quantity-control__row">
            <button
              type="button"
              className="quantity-control__step"
              aria-label="Decrease quantity"
              disabled={busy}
              onClick={() => run(() => (quantity === 1 ? removeItem(book.id) : updateItem(book.id, quantity - 1)))}
            >
              −
            </button>
            <span className="quantity-control__value" aria-live="polite">
              {quantity}
            </span>
            <button
              type="button"
              className="quantity-control__step"
              aria-label="Increase quantity"
              disabled={busy || atLimit}
              onClick={() => run(() => updateItem(book.id, quantity + 1))}
            >
              +
            </button>
            <Link to="/basket" className="quantity-control__cart">
              Go to Cart
            </Link>
          </div>
          {atLimit ? <p className="quantity-control__note">Maximum quantity reached</p> : null}
        </>
      )}
      {message ? (
        <p className="quantity-control__error" role="alert">
          {message}
        </p>
      ) : null}
    </div>
  );
}
