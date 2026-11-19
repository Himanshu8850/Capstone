import { useState } from 'react';
import { Link } from 'react-router-dom';
import { createBookReview } from '../../api/catalogueApi.js';
import { useAuth } from '../../context/AuthContext.jsx';
import { useFormSubmit } from '../../hooks/useFormSubmit.js';
import { returnToQuery } from '../../utils/returnTo.js';
import Button from '../common/Button.jsx';
import ErrorMessage from '../common/ErrorMessage.jsx';
import Field from '../common/Field.jsx';
import Spinner from '../common/Spinner.jsx';
import StarRating from './StarRating.jsx';
import './ReviewsSection.css';

const COMMENT_LIMIT = 100;
const RATING_MESSAGE = 'Select a rating from 1 to 5.';

function ReviewForm({ bookId, onSubmitted }) {
  const { submitting, formError, fieldErrors, setFieldErrors, clearFieldError, submit } = useFormSubmit();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');

  async function handleSubmit(event) {
    event.preventDefault();
    if (rating < 1) {
      setFieldErrors({ rating: RATING_MESSAGE });
      return;
    }
    const ok = await submit(() => createBookReview(bookId, { rating, comment: comment.trim() }));
    if (ok) {
      setRating(0);
      setComment('');
      onSubmitted();
    }
  }

  return (
    <form className="reviews__form" onSubmit={handleSubmit} noValidate aria-labelledby="review-form-heading">
      <h3 id="review-form-heading" className="reviews__form-heading">
        Leave Your Review
      </h3>
      <p className="field__required-note">* required</p>
      <fieldset className="reviews__rating" disabled={submitting}>
        <legend>
          Rating<span aria-hidden="true"> *</span>
        </legend>
        <div className="reviews__stars">
          {[1, 2, 3, 4, 5].map((value) => (
            <label key={value} className={`reviews__star${value <= rating ? ' reviews__star--on' : ''}`}>
              <input
                type="radio"
                name="rating"
                value={value}
                required
                checked={rating === value}
                onChange={() => {
                  setRating(value);
                  clearFieldError('rating');
                }}
              />
              <span aria-hidden="true">★</span>
              <span className="visually-hidden">{value === 1 ? '1 star' : `${value} stars`}</span>
            </label>
          ))}
        </div>
        {fieldErrors.rating ? (
          <p className="field__error" role="alert">
            {fieldErrors.rating}
          </p>
        ) : null}
      </fieldset>
      <Field
        label="Comment (optional)"
        name="comment"
        as="textarea"
        rows={3}
        value={comment}
        onChange={(event) => {
          setComment(event.target.value);
          clearFieldError('comment');
        }}
        error={fieldErrors.comment}
        hint={`${comment.length}/${COMMENT_LIMIT}`}
      />
      {formError ? <ErrorMessage message={formError} /> : null}
      <Button type="submit" disabled={submitting}>
        {submitting ? 'Submitting…' : 'Submit'}
      </Button>
    </form>
  );
}

function ReviewForGuest({ bookId }) {
  return (
    <div className="reviews__form">
      <h3 className="reviews__form-heading">Leave Your Review</h3>
      <p>
        <Link to={`/login${returnToQuery(`/books/${bookId}`)}`}>Log in to write a review</Link>
      </p>
    </div>
  );
}

/**
 * Reviews (AMD-002, T158): the form on the left (signed-in customers; guests get a log-in link) and the list on the
 * right. `reviews` is the result of `listBookReviews`; a successful submit reloads it. One review per book, no edit.
 */
export default function ReviewsSection({ bookId, reviews }) {
  const { customer } = useAuth();

  let list;
  if (reviews.error && !reviews.data) {
    list = <ErrorMessage message="The reviews could not be loaded." onRetry={reviews.retry} />;
  } else if (!reviews.data) {
    list = <Spinner label="Loading reviews" />;
  } else if (reviews.data.reviews.length === 0) {
    list = <p className="reviews__empty">No reviews yet.</p>;
  } else {
    list = (
      <ul className="reviews__list">
        {reviews.data.reviews.map((review) => (
          <li key={review.id} className="reviews__item">
            <p className="reviews__reviewer">
              <strong>{review.reviewerName}</strong>
            </p>
            <StarRating rating={review.rating} label={`${review.rating} out of 5 stars`} />
            {review.comment ? <p className="reviews__comment">{review.comment}</p> : null}
          </li>
        ))}
      </ul>
    );
  }

  return (
    <section className="reviews" aria-labelledby="reviews-heading">
      <h2 id="reviews-heading" className="reviews__heading">
        Reviews
      </h2>
      <div className="reviews__layout">
        {customer ? <ReviewForm bookId={bookId} onSubmitted={reviews.retry} /> : <ReviewForGuest bookId={bookId} />}
        <div className="reviews__results">{list}</div>
      </div>
    </section>
  );
}
