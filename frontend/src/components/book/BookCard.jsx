import { Link } from 'react-router-dom';
import { formatDeliveryDate } from '../../utils/date.js';
import { authorLink, categoryLink, formatLabel } from '../../utils/bookLinks.js';
import { formatMoney } from '../../utils/money.js';
import QuantityControl from '../basket/QuantityControl.jsx';
import WishlistButton from './WishlistButton.jsx';
import './BookCard.css';

/**
 * Catalogue card (FR-CAT-03, FR-CAT-07, FR-CAT-08): cover beside the details (title, author, short description,
 * format, genre tags, price, availability, delivery date), the wishlist heart and the cart control. The cover and
 * title open the book page; the author and each genre tag filter the catalogue. A physical book with no stock is
 * marked Unavailable and cannot be added.
 */
export default function BookCard({ book }) {
  const delivery = formatDeliveryDate(book.deliveryDate);
  const stock = book.format === 'EBOOK' ? 'Available' : book.available ? 'In stock' : 'Unavailable';

  return (
    <article className={`book-card${book.available ? '' : ' book-card--unavailable'}`}>
      <Link to={`/books/${book.id}`} className="book-card__cover-link" tabIndex={-1} aria-hidden="true">
        <img
          className="book-card__cover"
          src={book.frontCoverUrl}
          alt={`Cover of ${book.title}`}
          loading="lazy"
          width="160"
          height="240"
        />
      </Link>

      <div className="book-card__body">
        <div className="book-card__heading">
          <h3 className="book-card__title">
            <Link to={`/books/${book.id}`}>{book.title}</Link>
          </h3>
          <WishlistButton bookId={book.id} />
        </div>
        <p className="book-card__author">
          by <Link to={authorLink(book.author)}>{book.author}</Link>
        </p>
        <p className="book-card__description">{book.description}</p>

        <p className="book-card__meta">
          <span className="book-card__format">{formatLabel(book.format)}</span>
          <span className="book-card__language">{book.language}</span>
        </p>

        {book.categories.length > 0 ? (
          <ul className="book-card__tags" aria-label="Genres">
            {book.categories.map((category) => (
              <li key={category.id}>
                <Link to={categoryLink(category.id)} className="book-card__tag">
                  {category.name}
                </Link>
              </li>
            ))}
          </ul>
        ) : null}

        <p className="book-card__price">{formatMoney(book.price)}</p>
        <p className={`book-card__stock${book.available ? '' : ' book-card__stock--out'}`}>{stock}</p>
        {delivery ? <p className="book-card__delivery">Delivery by {delivery}</p> : null}
        <QuantityControl book={book} />
      </div>
    </article>
  );
}
