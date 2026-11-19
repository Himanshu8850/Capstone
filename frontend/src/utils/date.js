const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * Formats the contract's delivery date ("2026-07-21") as "Mon, 21 Jul" (FR-DET-02). The calendar date is
 * used as given, so the result never shifts with the browser's time zone.
 */
export function formatDeliveryDate(isoDate) {
  const match = ISO_DATE.exec(String(isoDate ?? ''));
  if (!match) return '';
  const [, year, month, day] = match;
  const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
  if (date.getUTCMonth() !== Number(month) - 1 || date.getUTCDate() !== Number(day)) return '';
  return `${DAYS[date.getUTCDay()]}, ${Number(day)} ${MONTHS[date.getUTCMonth()]}`;
}

const ORDER_DATE_FORMAT = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Kolkata',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

/** Formats an ISO-8601 instant (order placement time) in India time, for example "12 Nov 2026, 15:45". */
export function formatOrderDate(instant) {
  const date = new Date(instant);
  if (Number.isNaN(date.getTime())) return '';
  return ORDER_DATE_FORMAT.format(date);
}

const CANCELLATION_WINDOW_MS = 48 * 60 * 60 * 1000;

/**
 * Whether an order placed at `placedAt` is still strictly under 48 hours old (FR-ORD-04: exactly 48:00:00 is outside).
 * Only a hint for showing the Cancel action in a list; the server decides when the order is cancelled.
 */
export function isWithinCancellationWindow(placedAt, now = Date.now()) {
  const placed = new Date(placedAt).getTime();
  if (Number.isNaN(placed)) return false;
  return now - placed < CANCELLATION_WINDOW_MS;
}
