import { Link } from 'react-router-dom';
import { authorLink, formatLabel } from '../../utils/bookLinks.js';
import { formatMoney } from '../../utils/money.js';
import './OrderItemList.css';

/**
 * The books of an order as the snapshot taken when it was placed: cover, title, author, format, and
 * quantity x unit price = line total (SCR-10, SCR-11). Prices are never recalculated from the catalogue.
 */
export default function OrderItemList({ items }) {
  return (
    <ul className="order-items">
      {items.map((item) => {
        const { book } = item;
        return (
          <li key={book.id} className="order-items__item">
            <img src={book.frontCoverUrl} alt={`Cover of ${book.title}`} width="64" height="96" />
            <div className="order-items__details">
              <h3 className="order-items__title">
                <Link to={`/books/${book.id}`}>{book.title}</Link>
              </h3>
              <p className="order-items__muted">
                by <Link to={authorLink(book.author)}>{book.author}</Link> · {formatLabel(book.format)}
              </p>
              <p>
                {item.quantity} × {formatMoney(item.unitPrice)} = <strong>{formatMoney(item.lineTotal)}</strong>
              </p>
            </div>
          </li>
        );
      })}
    </ul>
  );
}
