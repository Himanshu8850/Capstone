import PageShell from '../components/layout/PageShell.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import { Link } from 'react-router-dom';

/** Not-found screen with a way back (FR-DET-09, SCR-13). */
export default function NotFoundPage() {
  return (
    <PageShell>
      <EmptyState title="Page not found" message="The page you are looking for does not exist.">
        <Link to="/">Back to the catalogue</Link>
      </EmptyState>
    </PageShell>
  );
}
