import { listBooks } from '../../api/catalogueApi.js';
import useAsync from '../../hooks/useAsync.js';
import ErrorMessage from '../common/ErrorMessage.jsx';
import Spinner from '../common/Spinner.jsx';
import BookCardCompact from './BookCardCompact.jsx';
import FollowButton from './FollowButton.jsx';
import './AboutTheWriter.css';

/**
 * About the writer (AMD-002, T159): the author's name with a Follow / Following button (frontend-only, localStorage)
 * and the author's other books, found with the catalogue's `author` filter and without the book being read. With no
 * other books only the name is shown.
 */
export default function AboutTheWriter({ book }) {
  const others = useAsync((signal) => listBooks({ author: book.author }, signal), [book.author]);
  const otherBooks = others.data ? others.data.items.filter((item) => item.id !== book.id) : [];

  return (
    <section className="writer" aria-labelledby="writer-heading">
      <h2 id="writer-heading" className="writer__heading">
        About the writer
      </h2>
      <div className="writer__who">
        <p className="writer__name">{book.author}</p>
        <FollowButton author={book.author} />
      </div>
      {book.authorBio ? <p className="writer__bio">{book.authorBio}</p> : null}
      {others.error ? (
        <ErrorMessage message="The writer's other books could not be loaded." onRetry={others.retry} />
      ) : !others.data ? (
        <Spinner label="Loading the writer's other books" />
      ) : otherBooks.length > 0 ? (
        <ul className="writer__books">
          {otherBooks.map((other) => (
            <li key={other.id}>
              <BookCardCompact book={other} />
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}
