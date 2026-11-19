import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { BasketContext, EMPTY_BASKET } from '../../../context/BasketContext.jsx';
import { ebook, outOfStock, paperback } from '../../../test/fixtures/index.js';
import BookCard from '../BookCard.jsx';
import BookCardCompact from '../BookCardCompact.jsx';

function renderCard(book, basketValue = {}) {
  const value = { basket: EMPTY_BASKET, itemCount: 0, addItem: vi.fn().mockResolvedValue(undefined), ...basketValue };
  return render(
    <MemoryRouter>
      <BasketContext.Provider value={value}>
        <BookCard book={book} />
      </BasketContext.Provider>
    </MemoryRouter>,
  );
}

describe('BookCard', () => {
  it('shows cover, title, author, description, format, genres, price, stock and delivery (FR-CAT-03)', () => {
    renderCard(paperback);

    expect(screen.getByRole('img', { name: 'Cover of The Habit Loop', hidden: true })).toHaveAttribute('src', '/covers/7-front.svg');
    expect(screen.getByRole('heading', { name: 'The Habit Loop' })).toBeInTheDocument();
    expect(screen.getByText(paperback.description)).toBeInTheDocument();
    expect(screen.getByText('Paperback')).toBeInTheDocument();
    expect(screen.getByText('English')).toBeInTheDocument();
    expect(screen.getByText('₹500.00')).toBeInTheDocument();
    expect(screen.getByText('In stock')).toBeInTheDocument();
    expect(screen.getByText('Delivery by Tue, 17 Nov')).toBeInTheDocument();
  });

  it('opens the book page from the title (FR-CAT-07)', () => {
    renderCard(paperback);
    expect(screen.getByRole('link', { name: 'The Habit Loop' })).toHaveAttribute('href', '/books/7');
  });

  it('filters by author and by genre instead of opening a profile page (FR-CAT-03, CL-25)', () => {
    renderCard(paperback);
    expect(screen.getByRole('link', { name: 'Meera Iyer' })).toHaveAttribute('href', '/?author=Meera+Iyer');
    const genres = screen.getByRole('list', { name: 'Genres' });
    expect(within(genres).getByRole('link', { name: 'Self Help' })).toHaveAttribute('href', '/?category=13');
  });

  it('marks a physical book with no stock as Unavailable (FR-CAT-08)', () => {
    const { container } = renderCard(outOfStock);
    expect(screen.getByText('Unavailable')).toBeInTheDocument();
    expect(screen.queryByText('In stock')).not.toBeInTheDocument();
    expect(container.querySelector('.book-card')).toHaveClass('book-card--unavailable');
  });

  it('shows an eBook as Available with the eBook format', () => {
    renderCard(ebook);
    expect(screen.getByText('eBook')).toBeInTheDocument();
    expect(screen.getByText('Available')).toBeInTheDocument();
    expect(screen.queryByText('Unavailable')).not.toBeInTheDocument();
  });

  it('encodes an author name that contains special characters in the link', () => {
    renderCard({ ...paperback, author: 'Smith & Sons / Co' });
    const href = screen.getByRole('link', { name: 'Smith & Sons / Co' }).getAttribute('href');
    expect(href).toBe('/?author=Smith+%26+Sons+%2F+Co');
  });

  it('renders text from the server as text, never as markup', () => {
    renderCard({ ...paperback, title: '<img src=x onerror=alert(1)>' });
    expect(screen.getByRole('heading', { name: '<img src=x onerror=alert(1)>' })).toBeInTheDocument();
    expect(document.querySelectorAll('img[src="x"]')).toHaveLength(0);
  });

  it('adds the book to the cart from the card (FR-BSK-01)', async () => {
    const addItem = vi.fn().mockResolvedValue(undefined);
    renderCard(paperback, { addItem });
    await userEvent.click(screen.getByRole('button', { name: 'Add to Cart' }));
    expect(addItem).toHaveBeenCalledWith(7, 1);
  });

  it('shows no cart control for a physical book with no stock and offers Add to Cart for an eBook', () => {
    const { unmount } = renderCard(outOfStock);
    expect(screen.queryByRole('button', { name: 'Add to Cart' })).not.toBeInTheDocument();
    unmount();
    renderCard(ebook);
    expect(screen.getByRole('button', { name: 'Add to Cart' })).toBeEnabled();
  });

  it('shows the quantity control once the book is in the cart, and explains a rejected change', async () => {
    const updateItem = vi.fn().mockRejectedValue({ code: 'QUANTITY_LIMIT', message: 'You can add at most 10 copies of one book.' });
    const basket = { ...EMPTY_BASKET, items: [{ book: paperback, quantity: 3, lineTotal: '1500.00' }], itemCount: 3 };
    renderCard(paperback, { updateItem, basket });
    expect(screen.getByText('3')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Increase quantity' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('You can add at most 10 copies of one book.');
  });

  it('toggles the book in the wishlist with the heart button (localStorage only)', async () => {
    renderCard(paperback);
    await userEvent.click(screen.getByRole('button', { name: 'Add to Wishlist' }));
    expect(screen.getByRole('button', { name: 'Remove from Wishlist' })).toBeInTheDocument();
    expect(JSON.parse(window.localStorage.getItem('bookygeek.wishlist'))).toEqual([7]);

    await userEvent.click(screen.getByRole('button', { name: 'Remove from Wishlist' }));
    expect(screen.getByRole('button', { name: 'Add to Wishlist' })).toBeInTheDocument();
    expect(JSON.parse(window.localStorage.getItem('bookygeek.wishlist'))).toEqual([]);
  });

  it('shows the cover beside the details so the card is horizontal', () => {
    const { container } = renderCard(paperback);
    const card = container.querySelector('.book-card');
    expect(card.children[0]).toHaveClass('book-card__cover-link');
    expect(card.children[1]).toHaveClass('book-card__body');
  });
});

describe('BookCardCompact', () => {
  it('shows cover, title, author, format, price and the cart control with working links', () => {
    render(
      <MemoryRouter>
        <BasketContext.Provider value={{ basket: EMPTY_BASKET, itemCount: 0, addItem: vi.fn() }}>
          <BookCardCompact book={ebook} />
        </BasketContext.Provider>
      </MemoryRouter>,
    );
    expect(screen.getByRole('img', { name: 'Cover of Quiet Mornings', hidden: true })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Quiet Mornings' })).toHaveAttribute('href', '/books/12');
    expect(screen.getByRole('link', { name: 'Meera Iyer' })).toHaveAttribute('href', '/?author=Meera+Iyer');
    expect(screen.getByText('eBook')).toBeInTheDocument();
    expect(screen.getByText('₹299.00')).toBeInTheDocument();
  });
});
