const WISHLIST_KEY = 'bookygeek.wishlist';
const WRITERS_KEY = 'bookygeek.followedWriters';
/** A tampered or runaway value never makes the wishlist page request more than this many books. */
const MAX_ITEMS = 100;

/**
 * A small list kept only in this browser's localStorage (no backend). Anything unreadable, malformed, duplicated or
 * invalid is dropped, and a corrupt value yields an empty list instead of throwing. `getSnapshot` returns the same
 * array until the stored value changes, so it can feed `useSyncExternalStore`.
 */
function createSavedList(key, isValidItem) {
  const listeners = new Set();
  let cachedRaw = null;
  let cachedItems = [];

  function parse(raw) {
    try {
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) return [];
      const items = [];
      for (const item of parsed) {
        if (isValidItem(item) && !items.includes(item)) items.push(item);
      }
      return items.slice(0, MAX_ITEMS);
    } catch {
      return [];
    }
  }

  function getSnapshot() {
    let raw = null;
    try {
      raw = window.localStorage.getItem(key);
    } catch {
      // Storage may be blocked; the list is then empty.
    }
    if (raw !== cachedRaw) {
      cachedRaw = raw;
      cachedItems = parse(raw);
    }
    return cachedItems;
  }

  function notify() {
    listeners.forEach((listener) => listener());
  }

  /** Adds the item when it is not in the list and removes it when it is. */
  function toggle(item) {
    if (!isValidItem(item)) return;
    const current = getSnapshot();
    let next;
    if (current.includes(item)) next = current.filter((existing) => existing !== item);
    else if (current.length >= MAX_ITEMS) return;
    else next = [...current, item];
    try {
      window.localStorage.setItem(key, JSON.stringify(next));
    } catch {
      // Storage may be full or blocked; the change is then simply not kept.
    }
    notify();
  }

  function subscribe(listener) {
    listeners.add(listener);
    // Other tabs change the same key.
    window.addEventListener('storage', listener);
    return () => {
      listeners.delete(listener);
      window.removeEventListener('storage', listener);
    };
  }

  return { getSnapshot, toggle, subscribe };
}

/** Wishlist: book ids (frontend-only, owner-approved under AMD-002). */
export const wishlist = createSavedList(WISHLIST_KEY, (id) => Number.isInteger(id) && id >= 1);

/** Followed writers: author names exactly as the catalogue `author` filter expects them. */
export const followedWriters = createSavedList(
  WRITERS_KEY,
  (name) => typeof name === 'string' && name.trim().length > 0 && name.length <= 200,
);
