/**
 * Reads the `returnTo` query value and returns a safe in-app path (A-12). Anything that could leave the site
 * (another origin, a protocol-relative URL, a backslash trick) or loop back to the auth pages falls back to `/`.
 */
export function safeReturnTo(value) {
  if (typeof value !== 'string' || value === '') return '/';
  if (!value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return '/';
  if (/[\u0000-\u001f]/.test(value)) return '/';
  const path = value.split(/[?#]/)[0];
  if (path === '/login' || path === '/register') return '/';
  return value;
}

/** `?returnTo=...` for a link between the login and register pages, or '' when the target is the home page. */
export function returnToQuery(safePath) {
  return safePath === '/' ? '' : `?returnTo=${encodeURIComponent(safePath)}`;
}
