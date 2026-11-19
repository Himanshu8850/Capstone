import { getRecommendations } from '../../api/basketApi.js';
import { useAuth } from '../../context/AuthContext.jsx';
import useAsync from '../../hooks/useAsync.js';
import BookCardCompact from './BookCardCompact.jsx';
import './RecommendedForYou.css';

function Recommendations({ customerId, refreshKey }) {
  const recommended = useAsync((signal) => getRecommendations(signal), [customerId, refreshKey]);

  if (!Array.isArray(recommended.data) || recommended.data.length === 0) return null;

  return (
    <section className="recommended" aria-labelledby="recommended-heading">
      <h2 id="recommended-heading" className="recommended__heading">
        Recommended for You
      </h2>
      <ul className="recommended__list">
        {recommended.data.map((book) => (
          <li key={book.id}>
            <BookCardCompact book={book} />
          </li>
        ))}
      </ul>
    </section>
  );
}

/**
 * "Recommended for You" (FR-LAND-03, FR-CAT-09, FR-BSK-07): listed, available books in the categories of the books the
 * customer has ordered before, without the books already in the basket. It is rendered only for a signed-in customer
 * and only when there is something to show: a guest (no request is made), a customer without order history, a failed
 * request and a request still loading all show nothing. `refreshKey` reloads the list when the basket changes.
 */
export default function RecommendedForYou({ refreshKey = '' }) {
  const { customer } = useAuth();
  if (!customer) return null;
  return <Recommendations customerId={customer.id} refreshKey={refreshKey} />;
}
