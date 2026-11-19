import useSavedList from '../../hooks/useSavedList.js';
import { followedWriters } from '../../utils/savedLists.js';
import './FollowButton.css';

/** Follow / Following toggle for a writer (frontend-only, kept in localStorage). */
export default function FollowButton({ author }) {
  const { has, toggle } = useSavedList(followedWriters);
  const following = has(author);

  return (
    <button
      type="button"
      className={`follow-button${following ? ' follow-button--following' : ''}`}
      onClick={() => toggle(author)}
    >
      {following ? 'Following' : 'Follow'}
    </button>
  );
}
