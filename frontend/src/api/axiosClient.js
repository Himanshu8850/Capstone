import axios from 'axios';

/** Paths whose 401 is an expected answer and must not trigger a redirect to the login page. */
const NO_REDIRECT_PATHS = ['/auth/me', '/auth/login', '/auth/register'];

/**
 * Turns any Axios failure into the one shape the UI works with:
 * `{ status, code, message, fieldErrors }`. `code` is the contract's ApiError code, or NETWORK_ERROR /
 * INTERNAL_ERROR when the server did not answer with an ApiError.
 */
export function normalizeError(error) {
  const data = error?.response?.data;
  if (data && typeof data === 'object' && typeof data.code === 'string') {
    return {
      status: data.status ?? error.response.status,
      code: data.code,
      message: data.message || 'Something went wrong. Please try again.',
      fieldErrors: Array.isArray(data.fieldErrors) ? data.fieldErrors : [],
    };
  }
  if (error?.response) {
    return {
      status: error.response.status,
      code: 'INTERNAL_ERROR',
      message: 'Something went wrong. Please try again later.',
      fieldErrors: [],
    };
  }
  return {
    status: 0,
    code: 'NETWORK_ERROR',
    message: 'We could not reach the server. Check your connection and try again.',
    fieldErrors: [],
  };
}

function redirectToLogin() {
  const { pathname, search, hash } = window.location;
  if (pathname === '/login') return;
  const returnTo = encodeURIComponent(`${pathname}${search}${hash}`);
  window.location.assign(`/login?returnTo=${returnTo}`);
}

/**
 * The only place an Axios instance is created. Cookie authentication: the browser sends BG_AUTH itself and
 * Axios copies the readable XSRF-TOKEN cookie into the X-XSRF-TOKEN header on every request.
 */
export function createApiClient({ onUnauthenticated = redirectToLogin, ...overrides } = {}) {
  const client = axios.create({
    baseURL: '/api/v1',
    withCredentials: true,
    timeout: 10000,
    xsrfCookieName: 'XSRF-TOKEN',
    xsrfHeaderName: 'X-XSRF-TOKEN',
    withXSRFToken: true,
    headers: { Accept: 'application/json' },
    ...overrides,
  });

  client.interceptors.response.use(
    (response) => response,
    (error) => {
      const normalized = normalizeError(error);
      const path = error?.config?.url ?? '';
      if (normalized.status === 401 && normalized.code === 'UNAUTHENTICATED' && !NO_REDIRECT_PATHS.includes(path)) {
        onUnauthenticated();
      }
      return Promise.reject(normalized);
    },
  );

  return client;
}

const apiClient = createApiClient();

export default apiClient;
