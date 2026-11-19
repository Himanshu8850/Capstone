import { Link } from 'react-router-dom';
import FollowButton from '../components/book/FollowButton.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import PageShell from '../components/layout/PageShell.jsx';
import useSavedList from '../hooks/useSavedList.js';
import { authorLink } from '../utils/bookLinks.js';
import { followedWriters } from '../utils/savedLists.js';
import './WritersPage.css';

/**
 * My Writers (/writers): the writers followed with the Follow button. The names live only in this browser
 * (localStorage, frontend-only). Each name opens the catalogue filtered to that writer's books (the existing
 * `author` filter of `listBooks`); the button next to it unfollows.
 */
export default function WritersPage() {
  const { items: writers } = useSavedList(followedWriters);

  return (
    <PageShell>
      <h1 className="writers__heading">My Writers</h1>
      {writers.length === 0 ? (
        <EmptyState title="You are not following any writers." message="Follow a writer from a book page to list them here.">
          <Link to="/">Start shopping</Link>
        </EmptyState>
      ) : (
        <ul className="writers__list">
          {writers.map((author) => (
            <li key={author} className="writers__item">
              <Link to={authorLink(author)} className="writers__name">
                {author}
              </Link>
              <FollowButton author={author} />
            </li>
          ))}
        </ul>
      )}
    </PageShell>
  );
}
