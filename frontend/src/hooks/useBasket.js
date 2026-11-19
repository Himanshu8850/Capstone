import { useCallback } from 'react';
import { useBasketContext } from '../context/BasketContext.jsx';

/**
 * The basket for components: everything the BasketContext offers plus `quantityOf(bookId)`, the units of a book
 * already in the basket (0 when it is not there).
 */
export default function useBasket() {
  const context = useBasketContext();
  const { basket } = context;

  const quantityOf = useCallback(
    (bookId) => basket.items.find((line) => line.book.id === bookId)?.quantity ?? 0,
    [basket],
  );

  return { ...context, quantityOf };
}
