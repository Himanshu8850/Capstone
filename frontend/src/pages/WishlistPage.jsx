import { Link } from 'react-router-dom';
import { getBook } from '../api/catalogueApi.js';
import BookCard from '../components/book/BookCard.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import Spinner from '../components/common/Spinner.jsx';
import PageShell from '../components/layout/PageShell.jsx';
import useAsync from '../hooks/useAsync.js';
import useSavedList from '../hooks/useSavedList.js';
import { wishlist } from '../utils/savedLists.js';
import './WishlistPage.css';

/**
 * My Wishlist (/wishlist): the books saved with the heart button. The saved ids live only in this browser
 * (localStorage, frontend-only); each book is read with the existing `getBook` operation, and a book that no longer
 * exists is left out. Removing a book with its heart takes it off the list.
 */
export default function WishlistPage() {
  const { items: ids } = useSavedList(wishlist);
  const key = ids.join(',');
  const books = useAsync(
    async (signal) => {
      const results = await Promise.allSettled(ids.map((id) => getBook(id, signal)));
      const found = results.filter((result) => result.status === 'fulfilled').map((result) => result.value);
      // Every request failed for a reason other than "not found": show the error instead of an empty list.
      const failed = results.find((result) => result.status === 'rejected' && result.reason?.code !== 'BOOK_NOT_FOUND');
      if (found.length === 0 && failed) throw failed.reason;
      return found;
    },
    [key],
  );

  let content;
  if (ids.length === 0) {
    content = (
      <EmptyState title="Your wishlist is empty." message="Tap the heart on a book to save it here.">
        <Link to="/">Start shopping</Link>
      </EmptyState>
    );
  } else if (books.error) {
    content = <ErrorMessage message="Your wishlist could not be loaded." onRetry={books.retry} />;
  } else if (!books.data) {
    content = <Spinner label="Loading your wishlist" />;
  } else {
    // A removed book disappears at once, before the list is read again.
    const shown = books.data.filter((book) => ids.includes(book.id));
    content = (
      <ul className="wishlist__list" aria-busy={books.loading}>
        {shown.map((book) => (
          <li key={book.id}>
            <BookCard book={book} />
          </li>
        ))}
      </ul>
    );
  }

  return (
    <PageShell>
      <h1 className="wishlist__heading">My Wishlist</h1>
      {content}
    </PageShell>
  );
}
