import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { BasketContext, EMPTY_BASKET } from '../../../context/BasketContext.jsx';
import { ebook, outOfStock, paperback } from '../../../test/fixtures/index.js';
import QuantityControl from '../QuantityControl.jsx';

function renderControl(book, quantity = 0, handlers = {}) {
  const basket = quantity
    ? { ...EMPTY_BASKET, items: [{ book, quantity, lineTotal: '0.00' }], itemCount: quantity }
    : EMPTY_BASKET;
  const value = {
    basket,
    itemCount: basket.itemCount,
    addItem: vi.fn().mockResolvedValue(undefined),
    updateItem: vi.fn().mockResolvedValue(undefined),
    removeItem: vi.fn().mockResolvedValue(undefined),
    ...handlers,
  };
  render(
    <MemoryRouter>
      <BasketContext.Provider value={value}>
        <QuantityControl book={book} />
      </BasketContext.Provider>
    </MemoryRouter>,
  );
  return value;
}

describe('QuantityControl', () => {
  it('adds one copy with Add to Cart when the book is not in the basket', async () => {
    const basket = renderControl(paperback);
    await userEvent.click(screen.getByRole('button', { name: 'Add to Cart' }));
    expect(basket.addItem).toHaveBeenCalledWith(7, 1);
    expect(screen.queryByRole('button', { name: 'Increase quantity' })).not.toBeInTheDocument();
  });

  it('shows minus, the quantity, plus and Go to Cart once the book is in the basket, and plus adds one', async () => {
    const basket = renderControl(paperback, 2);
    expect(screen.queryByRole('button', { name: 'Add to Cart' })).not.toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to Cart' })).toHaveAttribute('href', '/basket');

    await userEvent.click(screen.getByRole('button', { name: 'Increase quantity' }));
    expect(basket.updateItem).toHaveBeenCalledWith(7, 3);
  });

  it('minus lowers the quantity by one', async () => {
    const basket = renderControl(paperback, 2);
    await userEvent.click(screen.getByRole('button', { name: 'Decrease quantity' }));
    expect(basket.updateItem).toHaveBeenCalledWith(7, 1);
    expect(basket.removeItem).not.toHaveBeenCalled();
  });

  it('removes the line when minus is used at quantity 1', async () => {
    const basket = renderControl(paperback, 1);
    await userEvent.click(screen.getByRole('button', { name: 'Decrease quantity' }));
    expect(basket.removeItem).toHaveBeenCalledWith(7);
    expect(basket.updateItem).not.toHaveBeenCalled();
  });

  it('disables plus at the limit of 10 and says so', () => {
    renderControl(ebook, 10);
    expect(screen.getByRole('button', { name: 'Increase quantity' })).toBeDisabled();
    expect(screen.getByText('Maximum quantity reached')).toBeInTheDocument();
  });

  it('disables plus at the stock of a physical book when that is lower than 10', () => {
    renderControl({ ...paperback, stockQuantity: 3 }, 3);
    expect(screen.getByRole('button', { name: 'Increase quantity' })).toBeDisabled();
    expect(screen.getByText('Maximum quantity reached')).toBeInTheDocument();
  });

  it('shows no control for a physical book with no stock', () => {
    renderControl(outOfStock);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Go to Cart' })).not.toBeInTheDocument();
  });
});
