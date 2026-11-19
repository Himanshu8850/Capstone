import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import {
  addBasketItem,
  getBasket,
  mergeGuestBasket,
  previewBasket,
  removeBasketItem,
  updateBasketItem,
} from '../api/basketApi.js';
import { clearGuestBasket, readGuestBasket, writeGuestBasket } from '../utils/storage.js';
import { useAuth } from './AuthContext.jsx';

export const BasketContext = createContext(null);

export const MAX_QUANTITY = 10;

export const EMPTY_BASKET = {
  items: [],
  itemCount: 0,
  subtotal: '0.00',
  tax: '0.00',
  deliveryCharge: '0.00',
  total: '0.00',
};

async function previewGuestBasket(lines) {
  if (lines.length === 0) return EMPTY_BASKET;
  return previewBasket(lines);
}

/** An error in the same shape the API client produces, for rules the browser checks itself (guest basket). */
function basketError(code, message) {
  return { status: 409, code, message, fieldErrors: [] };
}

/** The most units of this book a basket line may hold: 10, or the stock of a physical book when lower. */
export function maxQuantityFor(book) {
  return book.stockQuantity === null || book.stockQuantity === undefined
    ? MAX_QUANTITY
    : Math.min(MAX_QUANTITY, book.stockQuantity);
}

/** A guest basket is priced without checking stock, so the limits of the contract are checked here. */
function assertWithinStock(basket, bookId) {
  const line = basket.items.find((item) => item.book.id === bookId);
  if (!line) return;
  const limit = maxQuantityFor(line.book);
  if (line.quantity > limit) {
    throw basketError(
      'OUT_OF_STOCK',
      limit === 0
        ? 'This book is out of stock.'
        : `Only ${limit} ${limit === 1 ? 'copy is' : 'copies are'} in stock.`,
    );
  }
}

/**
 * One basket API for guests and customers. A guest basket is `{bookId, quantity}` lines in localStorage priced
 * through the public `previewBasket`; a customer's basket lives on the server. When a customer signs in, the
 * guest lines are merged with `mergeGuestBasket` and cleared from the browser only after that call succeeds.
 */
export function BasketProvider({ children }) {
  const { customer, loading: authLoading } = useAuth();
  const customerId = customer ? customer.id : null;
  const [basket, setBasket] = useState(EMPTY_BASKET);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [mergeReport, setMergeReport] = useState(null);
  const requestId = useRef(0);

  const load = useCallback(async () => {
    const id = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const guestLines = readGuestBasket();
      let next;
      let report = null;
      if (customerId === null) {
        next = await previewGuestBasket(guestLines);
      } else if (guestLines.length > 0) {
        const merged = await mergeGuestBasket(guestLines);
        clearGuestBasket();
        next = merged.basket;
        report = merged.report;
      } else {
        next = await getBasket();
      }
      if (id !== requestId.current) return;
      setBasket(next);
      setMergeReport(report && report.adjustments.length > 0 ? report : null);
    } catch (caught) {
      if (id === requestId.current) setError(caught);
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, [customerId]);

  useEffect(() => {
    if (!authLoading) load();
  }, [authLoading, load]);

  /** Prices the guest lines, checks the limits and only then stores them and shows the result. */
  const commitGuestLines = useCallback(async (nextLines, bookId) => {
    const priced = await previewGuestBasket(nextLines);
    assertWithinStock(priced, bookId);
    writeGuestBasket(nextLines);
    setBasket(priced);
  }, []);

  const addItem = useCallback(
    async (bookId, quantity = 1) => {
      if (customerId !== null) {
        setBasket(await addBasketItem(bookId, quantity));
        return;
      }
      const lines = readGuestBasket();
      const existing = lines.find((line) => line.bookId === bookId);
      const nextQuantity = (existing ? existing.quantity : 0) + quantity;
      if (nextQuantity > MAX_QUANTITY) {
        throw basketError('QUANTITY_LIMIT', `You can add at most ${MAX_QUANTITY} copies of one book.`);
      }
      const nextLines = existing
        ? lines.map((line) => (line.bookId === bookId ? { bookId, quantity: nextQuantity } : line))
        : [...lines, { bookId, quantity: nextQuantity }];
      await commitGuestLines(nextLines, bookId);
    },
    [customerId, commitGuestLines],
  );

  const updateItem = useCallback(
    async (bookId, quantity) => {
      if (customerId !== null) {
        setBasket(await updateBasketItem(bookId, quantity));
        return;
      }
      if (quantity > MAX_QUANTITY) {
        throw basketError('QUANTITY_LIMIT', `You can add at most ${MAX_QUANTITY} copies of one book.`);
      }
      const nextLines = readGuestBasket().map((line) => (line.bookId === bookId ? { bookId, quantity } : line));
      await commitGuestLines(nextLines, bookId);
    },
    [customerId, commitGuestLines],
  );

  const removeItem = useCallback(
    async (bookId) => {
      if (customerId !== null) {
        setBasket(await removeBasketItem(bookId));
        return;
      }
      const nextLines = readGuestBasket().filter((line) => line.bookId !== bookId);
      await commitGuestLines(nextLines, bookId);
    },
    [customerId, commitGuestLines],
  );

  const dismissMergeReport = useCallback(() => setMergeReport(null), []);

  const value = useMemo(
    () => ({
      basket,
      itemCount: basket.itemCount,
      loading,
      error,
      mergeReport,
      addItem,
      updateItem,
      removeItem,
      refresh: load,
      dismissMergeReport,
    }),
    [basket, loading, error, mergeReport, addItem, updateItem, removeItem, load, dismissMergeReport],
  );

  return <BasketContext.Provider value={value}>{children}</BasketContext.Provider>;
}

export function useBasketContext() {
  const value = useContext(BasketContext);
  if (!value) throw new Error('useBasketContext must be used inside a BasketProvider.');
  return value;
}
