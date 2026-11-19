import { describe, expect, it, vi } from 'vitest';
import { createApiClient, normalizeError } from '../axiosClient.js';

/** Adapter that records the request config and answers with a fixed status and body. */
function fakeAdapter(status, data, seen = []) {
  return (config) => {
    seen.push(config);
    const response = { data, status, statusText: '', headers: {}, config, request: {} };
    if (status >= 200 && status < 300) return Promise.resolve(response);
    const error = new Error(`status ${status}`);
    error.config = config;
    error.response = response;
    return Promise.reject(error);
  };
}

const apiError = (status, code, extra = {}) => ({
  status,
  code,
  message: 'Server message',
  timestamp: '2026-11-12T10:15:30Z',
  ...extra,
});

describe('createApiClient configuration', () => {
  it('uses the contract base path, credentials, a 10 s timeout and the XSRF names', () => {
    const client = createApiClient();
    expect(client.defaults.baseURL).toBe('/api/v1');
    expect(client.defaults.withCredentials).toBe(true);
    expect(client.defaults.timeout).toBe(10000);
    expect(client.defaults.xsrfCookieName).toBe('XSRF-TOKEN');
    expect(client.defaults.xsrfHeaderName).toBe('X-XSRF-TOKEN');
  });

  it('echoes the XSRF-TOKEN cookie in the X-XSRF-TOKEN header (real XHR adapter, stubbed transport)', async () => {
    const sent = {};
    class FakeXhr {
      constructor() {
        this.onloadend = null;
        this.readyState = 4;
        this.status = 200;
        this.responseText = '{}';
        this.response = '{}';
        this.responseURL = '';
      }
      open(method, url) {
        sent.method = method;
        sent.url = url;
      }
      setRequestHeader(name, value) {
        sent[name.toLowerCase()] = value;
      }
      getAllResponseHeaders() {
        return 'content-type: application/json';
      }
      send() {
        queueMicrotask(() => this.onloadend && this.onloadend());
      }
      abort() {}
    }
    vi.stubGlobal('XMLHttpRequest', FakeXhr);
    document.cookie = 'XSRF-TOKEN=abc123; path=/';
    try {
      await createApiClient({ adapter: 'xhr' }).post('/basket/items', { bookId: 7 });
    } finally {
      document.cookie = 'XSRF-TOKEN=; path=/; max-age=0';
      vi.unstubAllGlobals();
    }
    expect(sent.method.toUpperCase()).toBe('POST');
    expect(sent.url).toBe('/api/v1/basket/items');
    expect(sent['x-xsrf-token']).toBe('abc123');
  });
});

describe('error handling', () => {
  it('maps an ApiError body to the normalised shape, keeping fieldErrors', async () => {
    const body = apiError(400, 'VALIDATION_FAILED', { fieldErrors: [{ field: 'phone', message: 'Bad phone' }] });
    const client = createApiClient({ adapter: fakeAdapter(400, body) });
    await expect(client.post('/addresses', {})).rejects.toEqual({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'Server message',
      fieldErrors: [{ field: 'phone', message: 'Bad phone' }],
    });
  });

  it('maps a non-ApiError server response to a generic INTERNAL_ERROR', () => {
    const error = { response: { status: 502, data: '<html>bad gateway</html>' } };
    expect(normalizeError(error)).toMatchObject({ status: 502, code: 'INTERNAL_ERROR', fieldErrors: [] });
    expect(normalizeError(error).message).not.toContain('html');
  });

  it('maps a failure with no response to NETWORK_ERROR', () => {
    expect(normalizeError(new Error('timeout of 10000ms exceeded'))).toMatchObject({
      status: 0,
      code: 'NETWORK_ERROR',
    });
  });
});

describe('401 handling', () => {
  it('calls the unauthenticated handler on 401 UNAUTHENTICATED for a protected path', async () => {
    const onUnauthenticated = vi.fn();
    const client = createApiClient({
      onUnauthenticated,
      adapter: fakeAdapter(401, apiError(401, 'UNAUTHENTICATED')),
    });
    await expect(client.get('/orders')).rejects.toMatchObject({ code: 'UNAUTHENTICATED' });
    expect(onUnauthenticated).toHaveBeenCalledTimes(1);
  });

  it.each(['/auth/me', '/auth/login'])('does not redirect for the expected 401 on %s', async (path) => {
    const onUnauthenticated = vi.fn();
    const client = createApiClient({
      onUnauthenticated,
      adapter: fakeAdapter(401, apiError(401, 'UNAUTHENTICATED')),
    });
    await expect(client.get(path)).rejects.toMatchObject({ status: 401 });
    expect(onUnauthenticated).not.toHaveBeenCalled();
  });

  it('does not redirect for INVALID_CREDENTIALS', async () => {
    const onUnauthenticated = vi.fn();
    const client = createApiClient({
      onUnauthenticated,
      adapter: fakeAdapter(401, apiError(401, 'INVALID_CREDENTIALS')),
    });
    await expect(client.post('/auth/login', {})).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' });
    expect(onUnauthenticated).not.toHaveBeenCalled();
  });

  it('does not redirect for non-401 errors', async () => {
    const onUnauthenticated = vi.fn();
    const client = createApiClient({
      onUnauthenticated,
      adapter: fakeAdapter(409, apiError(409, 'OUT_OF_STOCK')),
    });
    await expect(client.post('/basket/items', {})).rejects.toMatchObject({ code: 'OUT_OF_STOCK' });
    expect(onUnauthenticated).not.toHaveBeenCalled();
  });
});
