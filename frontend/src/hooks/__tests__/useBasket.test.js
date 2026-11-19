import { renderHook } from '@testing-library/react';
import { createElement } from 'react';
import { describe, expect, it } from 'vitest';
import { BasketContext, EMPTY_BASKET } from '../../context/BasketContext.jsx';
import { basket, ebook } from '../../test/fixtures/index.js';
import useBasket from '../useBasket.js';

function wrapperFor(value) {
  return function Wrapper({ children }) {
    return createElement(BasketContext.Provider, { value }, children);
  };
}

describe('useBasket', () => {
  it('returns the basket context and the units of a book already in the basket', () => {
    const { result } = renderHook(() => useBasket(), {
      wrapper: wrapperFor({ basket, itemCount: basket.itemCount }),
    });

    expect(result.current.itemCount).toBe(2);
    expect(result.current.quantityOf(7)).toBe(2);
  });

  it('reports 0 for a book that is not in the basket', () => {
    const { result } = renderHook(() => useBasket(), {
      wrapper: wrapperFor({ basket: EMPTY_BASKET, itemCount: 0 }),
    });

    expect(result.current.quantityOf(ebook.id)).toBe(0);
  });

  it('fails clearly when used outside a BasketProvider', () => {
    expect(() => renderHook(() => useBasket())).toThrow('useBasketContext must be used inside a BasketProvider.');
  });
});
