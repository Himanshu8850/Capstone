import { useSyncExternalStore } from 'react';

/** Reads one of the localStorage lists of `utils/savedLists.js` and re-renders when it changes. */
export default function useSavedList(list) {
  const items = useSyncExternalStore(list.subscribe, list.getSnapshot);
  return { items, has: (item) => items.includes(item), toggle: list.toggle };
}
