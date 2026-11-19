import Button from './Button.jsx';
import './Pagination.css';

/**
 * Previous / Next with the position ("Page 1 of 3"). `page` is zero-based, as in the API; the label is one-based.
 * Renders nothing when everything fits on one page.
 */
export default function Pagination({ page, totalPages, onPageChange }) {
  if (totalPages <= 1) return null;
  return (
    <nav className="pagination" aria-label="Pagination">
      <Button variant="secondary" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
        Previous
      </Button>
      <span className="pagination__position" aria-current="page">
        Page {page + 1} of {totalPages}
      </span>
      <Button variant="secondary" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>
        Next
      </Button>
    </nav>
  );
}
