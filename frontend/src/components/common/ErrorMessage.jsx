import Button from './Button.jsx';
import './ErrorMessage.css';

/** User-visible error (Principle VI). Shows a Retry button only when `onRetry` is supplied. */
export default function ErrorMessage({ message, onRetry }) {
  return (
    <div className="error-message" role="alert">
      <p className="error-message__text">{message || 'Something went wrong. Please try again.'}</p>
      {onRetry ? (
        <Button variant="secondary" onClick={onRetry}>
          Retry
        </Button>
      ) : null}
    </div>
  );
}
