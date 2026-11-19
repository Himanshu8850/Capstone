const GUEST_BASKET_KEY = 'bookygeek.guestBasket';
const MAX_QUANTITY = 10;

function isValidLine(line) {
  return (
    line !== null &&
    typeof line === 'object' &&
    Number.isInteger(line.bookId) &&
    line.bookId >= 1 &&
    Number.isInteger(line.quantity) &&
    line.quantity >= 1 &&
    line.quantity <= MAX_QUANTITY
  );
}

/**
 * Reads the guest basket as `[{ bookId, quantity }]`. Anything unreadable, malformed or out of range is
 * dropped, and a corrupt value yields an empty basket instead of throwing.
 */
export function readGuestBasket() {
  try {
    const parsed = JSON.parse(window.localStorage.getItem(GUEST_BASKET_KEY));
    if (!Array.isArray(parsed)) return [];
    const seen = new Set();
    const lines = [];
    for (const line of parsed) {
      if (isValidLine(line) && !seen.has(line.bookId)) {
        seen.add(line.bookId);
        lines.push({ bookId: line.bookId, quantity: line.quantity });
      }
    }
    return lines;
  } catch {
    return [];
  }
}

/** Stores the guest basket. Only `bookId` and `quantity` are ever written. */
export function writeGuestBasket(lines) {
  const clean = lines.filter(isValidLine).map(({ bookId, quantity }) => ({ bookId, quantity }));
  try {
    window.localStorage.setItem(GUEST_BASKET_KEY, JSON.stringify(clean));
  } catch {
    // Storage may be full or blocked; the basket then simply is not kept across reloads.
  }
}

/** Removes the guest basket (called only after a successful merge at login). */
export function clearGuestBasket() {
  try {
    window.localStorage.removeItem(GUEST_BASKET_KEY);
  } catch {
    // Nothing to clear when storage is unavailable.
  }
}
