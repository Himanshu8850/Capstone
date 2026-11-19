import useSavedList from '../../hooks/useSavedList.js';
import { wishlist } from '../../utils/savedLists.js';
import { HeartIcon } from '../common/Icons.jsx';
import './WishlistButton.css';

/**
 * Heart button of the wishlist (frontend-only, kept in localStorage). Icon-only on a book card; with `showLabel` it
 * shows "Add to Wishlist" / "Remove from Wishlist" next to the heart (book page).
 */
export default function WishlistButton({ bookId, showLabel = false }) {
  const { has, toggle } = useSavedList(wishlist);
  const saved = has(bookId);
  const label = saved ? 'Remove from Wishlist' : 'Add to Wishlist';

  return (
    <button
      type="button"
      className={`wishlist-button${saved ? ' wishlist-button--saved' : ''}${showLabel ? ' wishlist-button--labelled' : ''}`}
      aria-label={showLabel ? undefined : label}
      onClick={() => toggle(bookId)}
    >
      <HeartIcon filled={saved} />
      {showLabel ? <span>{label}</span> : null}
    </button>
  );
}
