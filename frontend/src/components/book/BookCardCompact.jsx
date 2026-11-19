import { Link } from 'react-router-dom';
import { authorLink, formatLabel } from '../../utils/bookLinks.js';
import { formatMoney } from '../../utils/money.js';
import QuantityControl from '../basket/QuantityControl.jsx';
import WishlistButton from './WishlistButton.jsx';
import './BookCardCompact.css';

/**
 * Small horizontal card for lists inside other screens (related books, recommendations, the writer's other books,
 * the wishlist): cover beside the title, author, format, price, the wishlist heart and the cart control. The cover and
 * title open the book page; the author filters the catalogue.
 */
export default function BookCardCompact({ book }) {
  return (
    <article className="book-card-compact">
      <Link to={`/books/${book.id}`} className="book-card-compact__cover-link" tabIndex={-1} aria-hidden="true">
        <img
          className="book-card-compact__cover"
          src={book.frontCoverUrl}
          alt={`Cover of ${book.title}`}
          loading="lazy"
          width="96"
          height="144"
        />
      </Link>
      <div className="book-card-compact__body">
        <div className="book-card-compact__heading">
          <h3 className="book-card-compact__title">
            <Link to={`/books/${book.id}`}>{book.title}</Link>
          </h3>
          <WishlistButton bookId={book.id} />
        </div>
        <p className="book-card-compact__author">
          by <Link to={authorLink(book.author)}>{book.author}</Link>
        </p>
        <p className="book-card-compact__format">{formatLabel(book.format)}</p>
        <p className="book-card-compact__price">{formatMoney(book.price)}</p>
        <QuantityControl book={book} />
      </div>
    </article>
  );
}
