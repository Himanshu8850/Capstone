import { useCallback, useState } from 'react';
import { buyAgain } from '../api/orderApi.js';
import useBasket from './useBasket.js';

/**
 * Buy Again for My Orders and the order page (FR-ORD-03). `run(order)` adds the order's books to the basket at their
 * current price and stock and keeps the add report in `result`, so the page can list what was not added or reduced.
 * The shared basket is refreshed afterwards, so the header badge shows the new count.
 */
export default function useBuyAgain() {
  const { refresh } = useBasket();
  const [state, setState] = useState({ busyId: null, result: null, error: null });

  const run = useCallback(
    async (order) => {
      setState({ busyId: order.id, result: null, error: null });
      try {
        const { report } = await buyAgain(order.id);
        await refresh();
        setState({ busyId: null, result: { reference: order.reference, adjustments: report.adjustments }, error: null });
      } catch (error) {
        setState({
          busyId: null,
          result: null,
          error: error?.message || 'The books could not be added to your basket. Please try again.',
        });
      }
    },
    [refresh],
  );

  const dismiss = useCallback(() => setState((current) => ({ ...current, result: null, error: null })), []);

  return { ...state, run, dismiss };
}
