const DECIMAL = /^(\d+)(?:\.(\d{1,2}))?$/;

/**
 * Formats a decimal string such as "1090.00" as "₹1,090.00" with Indian digit grouping (last three digits,
 * then groups of two). Works on the string only: no floating-point arithmetic is ever applied to money.
 * Returns an empty string for a value that is not a non-negative decimal string.
 */
export function formatMoney(value) {
  const match = DECIMAL.exec(String(value ?? '').trim());
  if (!match) return '';
  const whole = match[1].replace(/^0+(?=\d)/, '');
  const fraction = (match[2] ?? '').padEnd(2, '0');
  const lastThree = whole.slice(-3);
  const rest = whole.slice(0, -3);
  const grouped = rest ? `${rest.replace(/\B(?=(\d{2})+(?!\d))/g, ',')},${lastThree}` : lastThree;
  return `₹${grouped}.${fraction}`;
}

/**
 * Subtracts whole reward points (1 point = ₹1) from a money string, for example ("1090.00", 100) -> "990.00".
 * Works on whole paise as integers, so no floating-point arithmetic is applied to money. The result is never below
 * zero; a value that is not a non-negative decimal string is returned unchanged.
 */
export function subtractPoints(total, points) {
  const match = DECIMAL.exec(String(total ?? '').trim());
  if (!match) return total;
  const paise = BigInt(match[1]) * 100n + BigInt((match[2] ?? '').padEnd(2, '0') || '0');
  const left = paise - BigInt(Math.max(0, Math.trunc(Number(points) || 0))) * 100n;
  const value = left < 0n ? 0n : left;
  return `${value / 100n}.${String(value % 100n).padStart(2, '0')}`;
}

/** Formats a whole number of reward points, for example 1000 -> "1,000". Points are integers. */
export function formatPoints(points) {
  const n = Number(points);
  if (!Number.isInteger(n) || n < 0) return '';
  return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}
