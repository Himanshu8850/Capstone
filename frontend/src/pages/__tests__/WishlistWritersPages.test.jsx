import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/catalogueApi.js', () => ({ getBook: vi.fn() }));

import { getBook } from '../../api/catalogueApi.js';
import { AuthContext } from '../../context/AuthContext.jsx';
import { BasketContext, EMPTY_BASKET } from '../../context/BasketContext.jsx';
import { bookDetail } from '../../test/fixtures/index.js';
import { followedWriters, wishlist } from '../../utils/savedLists.js';
import WishlistPage from '../WishlistPage.jsx';
import WritersPage from '../WritersPage.jsx';

function Probe() {
  const location = useLocation();
  return <p data-testid="url">{`${location.pathname}${location.search}`}</p>;
}

function renderPage(page) {
  return render(
    <MemoryRouter>
      <AuthContext.Provider value={{ customer: null, loading: false, login: vi.fn(), logout: vi.fn() }}>
        <BasketContext.Provider value={{ basket: EMPTY_BASKET, itemCount: 0, addItem: vi.fn() }}>
          {page}
          <Probe />
        </BasketContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  getBook.mockResolvedValue(bookDetail);
});

describe('WishlistPage', () => {
  it('shows the empty state when nothing is saved and never calls the server', () => {
    renderPage(<WishlistPage />);
    expect(screen.getByRole('heading', { name: 'My Wishlist' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Your wishlist is empty.' })).toBeInTheDocument();
    expect(getBook).not.toHaveBeenCalled();
  });

  it('lists the saved books and drops one when its heart is pressed', async () => {
    window.localStorage.setItem('bookygeek.wishlist', JSON.stringify([7]));
    renderPage(<WishlistPage />);

    expect(await screen.findByRole('heading', { name: 'The Habit Loop' })).toBeInTheDocument();
    expect(getBook).toHaveBeenCalledWith(7, expect.anything());

    await userEvent.click(screen.getByRole('button', { name: 'Remove from Wishlist' }));
    expect(screen.queryByRole('heading', { name: 'The Habit Loop' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Your wishlist is empty.' })).toBeInTheDocument();
  });

  it('leaves out a saved book that no longer exists', async () => {
    window.localStorage.setItem('bookygeek.wishlist', JSON.stringify([7, 99]));
    getBook.mockImplementation(async (id) => {
      if (id === 99) throw { status: 404, code: 'BOOK_NOT_FOUND' };
      return bookDetail;
    });
    renderPage(<WishlistPage />);
    expect(await screen.findByRole('heading', { name: 'The Habit Loop' })).toBeInTheDocument();
    expect(screen.getAllByRole('article')).toHaveLength(1);
  });

  it('shows an error with Retry when every book fails to load', async () => {
    window.localStorage.setItem('bookygeek.wishlist', JSON.stringify([7]));
    getBook.mockRejectedValueOnce({ status: 500, code: 'INTERNAL_ERROR' });
    renderPage(<WishlistPage />);
    expect(await screen.findByText('Your wishlist could not be loaded.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('heading', { name: 'The Habit Loop' })).toBeInTheDocument();
  });
});

describe('WritersPage', () => {
  it('shows the empty state when no writer is followed', () => {
    renderPage(<WritersPage />);
    expect(screen.getByRole('heading', { name: 'My Writers' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'You are not following any writers.' })).toBeInTheDocument();
  });

  it('lists followed writers, links each to their books through the author filter, and unfollows', async () => {
    window.localStorage.setItem('bookygeek.followedWriters', JSON.stringify(['Meera Iyer']));
    renderPage(<WritersPage />);

    expect(screen.getByRole('link', { name: 'Meera Iyer' })).toHaveAttribute('href', '/?author=Meera+Iyer');
    await userEvent.click(screen.getByRole('button', { name: 'Following' }));
    expect(screen.getByRole('heading', { name: 'You are not following any writers.' })).toBeInTheDocument();
    expect(JSON.parse(window.localStorage.getItem('bookygeek.followedWriters'))).toEqual([]);
  });
});

describe('saved lists in localStorage', () => {
  it('ignores corrupt, duplicated and invalid values instead of throwing', () => {
    window.localStorage.setItem('bookygeek.wishlist', '{not json');
    expect(wishlist.getSnapshot()).toEqual([]);

    window.localStorage.setItem('bookygeek.wishlist', JSON.stringify([3, 3, 'x', -1, 1.5, null, 4]));
    expect(wishlist.getSnapshot()).toEqual([3, 4]);

    window.localStorage.setItem('bookygeek.followedWriters', JSON.stringify(['A', 'A', '', 5, '  ', 'B']));
    expect(followedWriters.getSnapshot()).toEqual(['A', 'B']);
  });

  it('toggle adds then removes and rejects an invalid item', () => {
    wishlist.toggle(5);
    expect(wishlist.getSnapshot()).toEqual([5]);
    wishlist.toggle('nope');
    expect(wishlist.getSnapshot()).toEqual([5]);
    wishlist.toggle(5);
    expect(wishlist.getSnapshot()).toEqual([]);
  });
});
