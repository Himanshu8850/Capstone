import './StarRating.css';

/** Read-only stars for a rating from 0 to 5 (a fractional average is shown as its nearest whole number of stars). */
export default function StarRating({ rating, label }) {
  const filled = Math.max(0, Math.min(5, Math.round(rating)));
  return (
    <span className="star-rating" role="img" aria-label={label ?? `${rating} out of 5 stars`}>
      <span aria-hidden="true">
        {'★'.repeat(filled)}
        <span className="star-rating__empty">{'★'.repeat(5 - filled)}</span>
      </span>
    </span>
  );
}
