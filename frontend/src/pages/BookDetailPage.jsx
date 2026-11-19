import { Link, useParams } from 'react-router-dom';
import { getBook, listBookReviews, listRelatedBooks } from '../api/catalogueApi.js';
import QuantityControl from '../components/basket/QuantityControl.jsx';
import AboutTheWriter from '../components/book/AboutTheWriter.jsx';
import BookCardCompact from '../components/book/BookCardCompact.jsx';
import ReviewsSection from '../components/book/ReviewsSection.jsx';
import WishlistButton from '../components/book/WishlistButton.jsx';
import StarRating from '../components/book/StarRating.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import Spinner from '../components/common/Spinner.jsx';
import PageShell from '../components/layout/PageShell.jsx';
import useAsync from '../hooks/useAsync.js';
import { authorLink, categoryLink, formatLabel } from '../utils/bookLinks.js';
import { formatDeliveryDate } from '../utils/date.js';
import { formatMoney } from '../utils/money.js';
import './BookDetailPage.css';

const VALID_ID = /^[1-9]\d{0,17}$/;

function Breadcrumb({ book }) {
  return (
    <nav className="book-detail__breadcrumb" aria-label="Breadcrumb">
      <ol>
        <li>
          <Link to="/">Home</Link>
        </li>
        {book.breadcrumb.map((category) => (
          <li key={category.id}>
            <Link to={categoryLink(category.id)}>{category.name}</Link>
          </li>
        ))}
        <li aria-current="page">{book.title}</li>
      </ol>
    </nav>
  );
}

function AverageRating({ reviews }) {
  if (!reviews.data || reviews.data.reviewCount === 0) return null;
  const { averageRating, reviewCount } = reviews.data;
  return (
    <p className="book-detail__rating">
      <StarRating rating={averageRating} label={`Average rating ${averageRating} out of 5`} />{' '}
      <span>({reviewCount} {reviewCount === 1 ? 'review' : 'reviews'})</span>
    </p>
  );
}

function Availability({ book }) {
  if (book.format === 'EBOOK') return <p className="book-detail__stock">Available</p>;
  return book.available ? (
    <p className="book-detail__stock">In stock</p>
  ) : (
    <p className="book-detail__stock book-detail__stock--out">Unavailable</p>
  );
}

function RelatedReads({ bookId }) {
  const related = useAsync((signal) => listRelatedBooks(bookId, signal), [bookId]);

  return (
    <aside className="book-detail__related" aria-labelledby="related-reads-heading">
      <h2 id="related-reads-heading" className="book-detail__related-heading">
        Related Reads
      </h2>
      {related.error ? (
        <ErrorMessage message="The related books could not be loaded." onRetry={related.retry} />
      ) : !related.data ? (
        <Spinner label="Loading related books" />
      ) : related.data.length === 0 ? (
        <p className="book-detail__related-empty">There are no related books yet.</p>
      ) : (
        <ul className="book-detail__related-list">
          {related.data.map((book) => (
            <li key={book.id}>
              <BookCardCompact book={book} />
            </li>
          ))}
        </ul>
      )}
    </aside>
  );
}

function NotFound() {
  return (
    <EmptyState title="Book not found" message="This book does not exist or is no longer available.">
      <Link to="/">Back to the catalogue</Link>
    </EmptyState>
  );
}

/**
 * Book details and Related Reads (/books/:id, FR-DET-01..09, SCR-05). Guests can open it. It shows the covers, title,
 * author link, publisher, format, language, genre links, price, availability, the delivery date and a breadcrumb that
 * follows the category hierarchy, the wishlist button and the cart control. Related Reads is a right-hand section that
 * runs the full height of the page (the other books in the same category). Below the details come About the writer
 * (the author's other books) and the Reviews with the average rating.
 */
export default function BookDetailPage() {
  const { id } = useParams();
  const validId = VALID_ID.test(id ?? '');
  const result = useAsync((signal) => (validId ? getBook(id, signal) : Promise.resolve(null)), [id]);
  const reviews = useAsync((signal) => (validId ? listBookReviews(id, signal) : Promise.resolve(null)), [id]);
  const book = result.data;
  const notFound = !validId || result.error?.code === 'BOOK_NOT_FOUND' || (!result.loading && !result.error && !book);

  let content;
  let aside = null;
  if (notFound) {
    content = <NotFound />;
  } else if (result.error) {
    content = <ErrorMessage message="The book could not be loaded." onRetry={result.retry} />;
  } else if (!book || result.loading) {
    content = <Spinner label="Loading book" />;
  } else {
    const delivery = formatDeliveryDate(book.deliveryDate);
    aside = <RelatedReads bookId={book.id} />;
    content = (
      <>
        <Breadcrumb book={book} />
        <article className="book-detail__main">
          <div className="book-detail__covers">
            <img className="book-detail__cover" src={book.frontCoverUrl} alt={`Front cover of ${book.title}`} />
            {book.backCoverUrl ? (
              <img className="book-detail__cover" src={book.backCoverUrl} alt={`Back cover of ${book.title}`} />
            ) : null}
          </div>

          <div className="book-detail__info">
            <h1 className="book-detail__title">{book.title}</h1>
            <p className="book-detail__author">
              by <Link to={authorLink(book.author)}>{book.author}</Link>
            </p>
            <p className="book-detail__publisher">Published by: {book.publisher}</p>
            <p className="book-detail__description">{book.description}</p>

            <dl className="book-detail__facts">
              <div>
                <dt>Format</dt>
                <dd>{formatLabel(book.format)}</dd>
              </div>
              <div>
                <dt>Language</dt>
                <dd>{book.language}</dd>
              </div>
            </dl>

            {book.categories.length > 0 ? (
              <ul className="book-detail__tags" aria-label="Genres">
                {book.categories.map((category) => (
                  <li key={category.id}>
                    <Link to={categoryLink(category.id)} className="book-detail__tag">
                      {category.name}
                    </Link>
                  </li>
                ))}
              </ul>
            ) : null}

            <p className="book-detail__price">{formatMoney(book.price)}</p>
            <AverageRating reviews={reviews} />
            <Availability book={book} />
            {delivery ? <p className="book-detail__delivery">Delivery by {delivery}</p> : null}
            <QuantityControl book={book} />
            <div className="book-detail__wishlist">
              <WishlistButton bookId={book.id} showLabel />
            </div>
          </div>
        </article>
        <AboutTheWriter book={book} />
        <ReviewsSection bookId={book.id} reviews={reviews} />
      </>
    );
  }

  return <PageShell aside={aside}>{content}</PageShell>;
}