import './EmptyState.css';

/** Empty-result message with an optional action (children), for example a link back to browsing. */
export default function EmptyState({ title, message, children }) {
  return (
    <div className="empty-state">
      <h2 className="empty-state__title">{title}</h2>
      {message ? <p className="empty-state__message">{message}</p> : null}
      {children}
    </div>
  );
}
