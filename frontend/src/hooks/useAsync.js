import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Runs an async function and exposes `{ data, error, loading, retry }`.
 * - Runs on mount and again whenever `deps` change; `retry()` re-runs it.
 * - The function receives an AbortSignal and a result from a superseded or unmounted run is ignored.
 * - `error` is whatever the function rejected with (the normalised API error from axiosClient).
 */
export default function useAsync(asyncFn, deps = []) {
  const [state, setState] = useState({ data: null, error: null, loading: true });
  const [attempt, setAttempt] = useState(0);
  const fnRef = useRef(asyncFn);
  fnRef.current = asyncFn;

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    setState((previous) => ({ data: previous.data, error: null, loading: true }));
    Promise.resolve()
      .then(() => (active ? fnRef.current(controller.signal) : undefined))
      .then(
        (data) => {
          if (active) setState({ data, error: null, loading: false });
        },
        (error) => {
          if (active) setState({ data: null, error, loading: false });
        },
      );
    return () => {
      active = false;
      controller.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, attempt]);

  const retry = useCallback(() => setAttempt((n) => n + 1), []);

  return { ...state, retry };
}
