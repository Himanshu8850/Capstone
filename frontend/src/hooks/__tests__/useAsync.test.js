import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import useAsync from '../useAsync.js';

describe('useAsync', () => {
  it('starts loading and then exposes the data', async () => {
    const { result } = renderHook(() => useAsync(() => Promise.resolve({ ok: true })));
    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toEqual({ ok: true });
    expect(result.current.error).toBeNull();
  });

  it('exposes the rejection as error and retry runs the function again', async () => {
    const fn = vi
      .fn()
      .mockRejectedValueOnce({ code: 'NETWORK_ERROR', message: 'down' })
      .mockResolvedValueOnce('recovered');
    const { result } = renderHook(() => useAsync(fn));

    await waitFor(() => expect(result.current.error).toEqual({ code: 'NETWORK_ERROR', message: 'down' }));
    expect(result.current.data).toBeNull();

    act(() => result.current.retry());
    expect(result.current.loading).toBe(true);
    expect(result.current.error).toBeNull();
    await waitFor(() => expect(result.current.data).toBe('recovered'));
    expect(fn).toHaveBeenCalledTimes(2);
  });

  it('re-runs when dependencies change', async () => {
    const fn = vi.fn((signal) => Promise.resolve(signal.aborted));
    const { result, rerender } = renderHook(({ page }) => useAsync(fn, [page]), { initialProps: { page: 0 } });
    await waitFor(() => expect(result.current.loading).toBe(false));
    rerender({ page: 1 });
    await waitFor(() => expect(fn).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(result.current.loading).toBe(false));
  });

  it('ignores the result of a superseded run', async () => {
    let resolveFirst;
    const fn = vi
      .fn()
      .mockImplementationOnce(() => new Promise((resolve) => (resolveFirst = resolve)))
      .mockImplementationOnce(() => Promise.resolve('second'));
    const { result, rerender } = renderHook(({ q }) => useAsync(fn, [q]), { initialProps: { q: 'a' } });
    await waitFor(() => expect(fn).toHaveBeenCalledTimes(1));
    rerender({ q: 'b' });
    await waitFor(() => expect(result.current.data).toBe('second'));
    await act(async () => resolveFirst('first'));
    expect(result.current.data).toBe('second');
  });

  it('does not update state after unmount', async () => {
    let resolve;
    const { result, unmount } = renderHook(() => useAsync(() => new Promise((r) => (resolve = r))));
    await waitFor(() => expect(resolve).toBeDefined());
    const before = result.current;
    unmount();
    await act(async () => resolve('late'));
    expect(result.current).toBe(before);
  });

  it('does not call the function at all when unmounted before it starts', async () => {
    const fn = vi.fn(() => Promise.resolve('x'));
    const { unmount } = renderHook(() => useAsync(fn));
    unmount();
    await act(async () => {});
    expect(fn).not.toHaveBeenCalled();
  });

  it('passes an AbortSignal that is aborted on unmount', async () => {
    let seen;
    const { unmount } = renderHook(() =>
      useAsync((signal) => {
        seen = signal;
        return new Promise(() => {});
      }),
    );
    await waitFor(() => expect(seen).toBeDefined());
    expect(seen.aborted).toBe(false);
    unmount();
    expect(seen.aborted).toBe(true);
  });
});
