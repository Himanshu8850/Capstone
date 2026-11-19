import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/catalogueApi.js', () => ({
  listBooks: vi.fn(),
  listCategories: vi.fn(),
  listBrands: vi.fn(),
  getBookFilterOptions: vi.fn(),
}));

import { getBookFilterOptions, listBooks, listBrands, listCategories } from '../../api/catalogueApi.js';
import { AuthContext } from '../../context/AuthContext.jsx';
import { BasketContext, EMPTY_BASKET } from '../../context/BasketContext.jsx';
import { bookPage, brands, categories, emptyBookPage, filterOptions } from '../../test/fixtures/index.js';
import CataloguePage from '../CataloguePage.jsx';

function Probe() {
  const location = useLocation();
  return <p data-testid="url">{`${location.pathname}${location.search}`}</p>;
}

function renderPage(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthContext.Provider value={{ customer: null, loading: false, login: vi.fn(), logout: vi.fn() }}>
        <BasketContext.Provider value={{ basket: EMPTY_BASKET, itemCount: 0 }}>
          <CataloguePage />
          <Probe />
        </BasketContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}

const lastBooksParams = () => listBooks.mock.calls.at(-1)[0];

beforeEach(() => {
  vi.clearAllMocks();
  listCategories.mockResolvedValue(categories);
  listBrands.mockResolvedValue(brands);
  getBookFilterOptions.mockResolvedValue(filterOptions);
  listBooks.mockResolvedValue(bookPage);
});

describe('CataloguePage', () => {
  it('shows the catalogue, the category tree with All, and the Brands list to a guest', async () => {
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Catalogue' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'The Habit Loop' })).toBeInTheDocument();

    const nav = screen.getByRole('navigation', { name: 'Categories' });
    expect(within(nav).getByRole('button', { name: 'All' })).toHaveAttribute('aria-current', 'true');
    expect(within(nav).getByRole('button', { name: 'Non-Fiction' })).toBeInTheDocument();
    expect(within(nav).getByRole('button', { name: 'Self Help' })).toBeInTheDocument();
    const brandNav = screen.getByRole('navigation', { name: 'Brands' });
    expect(within(brandNav).getByRole('button', { name: /Maple Leaf Press/ })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('14 books');
  });

  it('selecting a category sets it in the URL, marks it selected and asks for it', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'The Habit Loop' });
    await userEvent.click(within(screen.getByRole('navigation', { name: 'Categories' })).getByRole('button', { name: 'Self Help' }));

    expect(screen.getByTestId('url')).toHaveTextContent('/?category=13');
    expect(lastBooksParams()).toMatchObject({ category: '13', page: undefined });
    expect(within(screen.getByRole('navigation', { name: 'Categories' })).getByRole('button', { name: 'Self Help' }))
      .toHaveAttribute('aria-current', 'true');
  });

  it('selecting a brand filters by it and "All brands" clears it', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'The Habit Loop' });
    const brandNav = screen.getByRole('navigation', { name: 'Brands' });
    await userEvent.click(within(brandNav).getByRole('button', { name: /Northwind Books/ }));
    expect(lastBooksParams()).toMatchObject({ brand: 'Northwind Books' });

    await userEvent.click(within(screen.getByRole('navigation', { name: 'Brands' })).getByRole('button', { name: 'All brands' }));
    expect(lastBooksParams().brand).toBeUndefined();
  });

  it('searches by text from the header and keeps it in the URL', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'The Habit Loop' });
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search the catalogue' }), '  habit  {enter}');
    expect(lastBooksParams()).toMatchObject({ q: 'habit' });
    expect(screen.getByTestId('url')).toHaveTextContent('/?q=habit');
  });

  it('applies Language, Format and Price filters together and each defaults to All', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'The Habit Loop' });
    expect(screen.getByLabelText('Language')).toHaveValue('');
    expect(screen.getByLabelText('Format')).toHaveValue('');
    expect(screen.getByLabelText('Price')).toHaveValue('');

    await userEvent.selectOptions(screen.getByLabelText('Language'), 'Hindi');
    await userEvent.selectOptions(screen.getByLabelText('Format'), 'EBOOK');
    await userEvent.selectOptions(screen.getByLabelText('Price'), '₹200–₹499');

    expect(lastBooksParams()).toMatchObject({
      language: 'Hindi',
      format: 'EBOOK',
      minPrice: '200.00',
      maxPrice: '499.99',
    });
  });

  it('an open-ended price band sends only the bound it has', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'The Habit Loop' });
    await userEvent.selectOptions(screen.getByLabelText('Price'), 'Under ₹200');
    expect(lastBooksParams()).toMatchObject({ maxPrice: '199.99' });
    expect(lastBooksParams().minPrice).toBeUndefined();
  });

  it('offers the four sort options with Relevance as the default and applies a choice', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'The Habit Loop' });
    const sort = screen.getByLabelText('Sort by');
    expect(sort).toHaveValue('RELEVANCE');
    expect(within(sort).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Relevance',
      'Price: Low to High',
      'Price: High to Low',
      'Title: A to Z',
    ]);
    await userEvent.selectOptions(sort, 'PRICE_DESC');
    expect(lastBooksParams()).toMatchObject({ sort: 'PRICE_DESC' });
  });

  it('pages with Previous and Next, keeps the filters, and returns to page 1 when a filter changes', async () => {
    listBooks.mockImplementation(async (params) => ({ ...bookPage, page: params.page ?? 0 }));
    renderPage('/?language=English&page=1');
    await screen.findByRole('heading', { name: 'The Habit Loop' });
    expect(lastBooksParams()).toMatchObject({ language: 'English', page: 1 });

    await userEvent.click(await screen.findByRole('button', { name: 'Previous' }));
    await waitFor(() => expect(lastBooksParams()).toMatchObject({ language: 'English', page: undefined }));
    expect(screen.getByTestId('url')).toHaveTextContent('/?language=English');

    await userEvent.click(await screen.findByRole('button', { name: 'Next' }));
    await waitFor(() => expect(lastBooksParams()).toMatchObject({ language: 'English', page: 1 }));

    await userEvent.selectOptions(screen.getByLabelText('Format'), 'EBOOK');
    await waitFor(() =>
      expect(lastBooksParams()).toMatchObject({ language: 'English', format: 'EBOOK', page: undefined }),
    );
  });

  it('restores the filters from the URL', async () => {
    renderPage('/?category=13&brand=Maple%20Leaf%20Press&q=habit&language=English&format=PAPERBACK&sort=TITLE_ASC');
    await screen.findByRole('heading', { name: 'The Habit Loop' });
    expect(lastBooksParams()).toEqual({
      category: '13',
      brand: 'Maple Leaf Press',
      q: 'habit',
      language: 'English',
      format: 'PAPERBACK',
      sort: 'TITLE_ASC',
      page: undefined,
    });
    expect(screen.getByLabelText('Language')).toHaveValue('English');
    expect(screen.getByLabelText('Sort by')).toHaveValue('TITLE_ASC');
    expect(screen.getByRole('searchbox', { name: 'Search the catalogue' })).toHaveValue('habit');
  });

  it('shows the author filter as a removable chip', async () => {
    renderPage('/?author=Meera%20Iyer');
    await screen.findByRole('heading', { name: 'The Habit Loop' });
    expect(screen.getByText('Meera Iyer', { selector: 'strong' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Remove author filter' }));
    expect(lastBooksParams().author).toBeUndefined();
  });

  it('shows an empty state with a way to clear the filters when nothing matches', async () => {
    listBooks.mockResolvedValue(emptyBookPage);
    renderPage('/?q=zzz');
    expect(await screen.findByRole('heading', { name: 'No books found' })).toBeInTheDocument();

    listBooks.mockResolvedValue(bookPage);
    await userEvent.click(screen.getAllByRole('button', { name: 'Clear filters' })[0]);
    expect(await screen.findByRole('heading', { name: 'The Habit Loop' })).toBeInTheDocument();
    expect(screen.getByTestId('url')).toHaveTextContent(/^\/$/);
  });

  it('shows the plain empty state when the catalogue itself is empty', async () => {
    listBooks.mockResolvedValue(emptyBookPage);
    renderPage();
    expect(await screen.findByText('There are no books in the catalogue yet.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Clear filters' })).not.toBeInTheDocument();
  });

  it('shows a loading indicator, then an error with Retry that reloads the books', async () => {
    listBooks.mockRejectedValueOnce({ status: 500, code: 'INTERNAL_ERROR' });
    renderPage();
    expect(screen.getByText('Loading books')).toBeInTheDocument();

    expect(await screen.findByText('The books could not be loaded.')).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('button', { name: 'Retry' })[0]);
    expect(await screen.findByRole('heading', { name: 'The Habit Loop' })).toBeInTheDocument();
    expect(listBooks).toHaveBeenCalledTimes(2);
  });

  it('keeps the page usable when the categories fail to load, and retries them', async () => {
    listCategories.mockRejectedValueOnce({ status: 500, code: 'INTERNAL_ERROR' });
    renderPage();
    expect(await screen.findByText('The categories could not be loaded.')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'The Habit Loop' })).toBeInTheDocument();

    await userEvent.click(within(screen.getByRole('navigation', { name: 'Categories' })).getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('button', { name: 'Non-Fiction' })).toBeInTheDocument();
  });

  it('disables the option filters until their options have loaded', async () => {
    getBookFilterOptions.mockReturnValue(new Promise(() => {}));
    renderPage();
    await screen.findByRole('heading', { name: 'The Habit Loop' });
    expect(screen.getByLabelText('Language')).toBeDisabled();
    expect(screen.getByLabelText('Sort by')).toBeEnabled();
  });

  it('toggles the category sidebar from the header menu icon', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'The Habit Loop' });
    const sidebar = screen.getByRole('complementary', { name: 'Browse by' });
    expect(sidebar).toHaveClass('sidebar--open');
    await userEvent.click(screen.getByRole('button', { name: 'Categories' }));
    expect(sidebar).not.toHaveClass('sidebar--open');
  });
});
