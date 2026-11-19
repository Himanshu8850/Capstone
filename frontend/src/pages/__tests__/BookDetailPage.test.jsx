import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/catalogueApi.js', () => ({
  getBook: vi.fn(),
  listRelatedBooks: vi.fn(),
  listBookReviews: vi.fn(),
  createBookReview: vi.fn(),
  listBooks: vi.fn(),
  listCategories: vi.fn(),
  listBrands: vi.fn(),
  getBookFilterOptions: vi.fn(),
}));

import { createBookReview, getBook, listBookReviews, listBooks, listRelatedBooks } from '../../api/catalogueApi.js';
import { AuthContext } from '../../context/AuthContext.jsx';
import { BasketContext, EMPTY_BASKET } from '../../context/BasketContext.jsx';
import { bookDetail, bookPage, ebook, ebookDetail, outOfStockDetail, paperback } from '../../test/fixtures/index.js';
import BookDetailPage from '../BookDetailPage.jsx';

function Probe() {
  const location = useLocation();
  return <p data-testid="url">{`${location.pathname}${location.search}`}</p>;
}

function renderPage(path = '/books/7', basketValue = {}, customer = null) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthContext.Provider value={{ customer, loading: false, login: vi.fn(), logout: vi.fn() }}>
        <BasketContext.Provider value={{ basket: EMPTY_BASKET, itemCount: 0, addItem: vi.fn(), ...basketValue }}>
          <Routes>
            <Route path="/books/:id" element={<BookDetailPage />} />
            <Route path="*" element={<p>Elsewhere</p>} />
          </Routes>
          <Probe />
        </BasketContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  getBook.mockResolvedValue(bookDetail);
  listRelatedBooks.mockResolvedValue([ebook]);
  listBookReviews.mockResolvedValue({ averageRating: null, reviewCount: 0, reviews: [] });
  listBooks.mockResolvedValue({ ...bookPage, items: [paperback] });
});

describe('BookDetailPage', () => {
  it('shows the details of a physical book: covers, title, author, publisher, format, language, genres, price, stock and delivery (FR-DET-01, FR-DET-02)', async () => {
    listRelatedBooks.mockResolvedValue([]);
    renderPage();

    expect(await screen.findByRole('heading', { level: 1, name: 'The Habit Loop' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Front cover of The Habit Loop' })).toHaveAttribute('src', '/covers/7-front.svg');
    expect(screen.getByRole('img', { name: 'Back cover of The Habit Loop' })).toHaveAttribute('src', '/covers/7-back.svg');
    expect(screen.getByRole('link', { name: 'Meera Iyer' })).toHaveAttribute('href', '/?author=Meera+Iyer');
    expect(screen.getByText('Published by: Maple Leaf Press')).toBeInTheDocument();
    expect(screen.getByText(paperback.description)).toBeInTheDocument();
    expect(screen.getByText('Paperback')).toBeInTheDocument();
    expect(screen.getByText('English')).toBeInTheDocument();
    expect(screen.getByText('₹500.00')).toBeInTheDocument();
    expect(screen.getByText('In stock')).toBeInTheDocument();
    expect(screen.getByText('Delivery by Tue, 17 Nov')).toBeInTheDocument();
    expect(getBook).toHaveBeenCalledWith('7', expect.anything());
  });

  it('genre tags filter the catalogue by that genre (FR-DET-01, CL-25)', async () => {
    renderPage();
    const genres = await screen.findByRole('list', { name: 'Genres' });
    expect(within(genres).getByRole('link', { name: 'Self Help' })).toHaveAttribute('href', '/?category=13');
  });

  it('shows an eBook as Available with today as the delivery date and no back cover when there is none', async () => {
    getBook.mockResolvedValue(ebookDetail);
    listRelatedBooks.mockResolvedValue([]);
    renderPage('/books/12');

    expect(await screen.findByRole('heading', { level: 1, name: 'Quiet Mornings' })).toBeInTheDocument();
    expect(screen.getByText('eBook')).toBeInTheDocument();
    expect(screen.getByText('Available')).toBeInTheDocument();
    expect(screen.queryByText('Unavailable')).not.toBeInTheDocument();
    expect(screen.getByText('Delivery by Thu, 12 Nov')).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /Back cover/ })).not.toBeInTheDocument();
  });

  it('marks a physical book with no stock as Unavailable (FR-CAT-08)', async () => {
    getBook.mockResolvedValue(outOfStockDetail);
    renderPage('/books/31');

    expect(await screen.findByRole('heading', { level: 1, name: 'Out of Print Atlas' })).toBeInTheDocument();
    expect(screen.getByText('Unavailable')).toBeInTheDocument();
    expect(screen.queryByText('In stock')).not.toBeInTheDocument();
  });

  it('shows a breadcrumb that follows the category hierarchy, and every level opens the catalogue there (FR-DET-06, CL-25)', async () => {
    renderPage();
    const crumbs = await screen.findByRole('navigation', { name: 'Breadcrumb' });

    const links = within(crumbs).getAllByRole('link');
    expect(links.map((link) => link.textContent)).toEqual(['Home', 'Non-Fiction', 'Self Help']);
    expect(links.map((link) => link.getAttribute('href'))).toEqual(['/', '/?category=2', '/?category=13']);
    expect(within(crumbs).getByText('The Habit Loop')).toHaveAttribute('aria-current', 'page');

    await userEvent.click(within(crumbs).getByRole('link', { name: 'Non-Fiction' }));
    expect(screen.getByTestId('url')).toHaveTextContent('/?category=2');
  });

  it('shows Related Reads as compact cards and opens a related book from its title (FR-DET-04, FR-DET-05)', async () => {
    renderPage();
    const related = await screen.findByRole('complementary', { name: 'Related Reads' });

    expect(await within(related).findByRole('heading', { name: 'Quiet Mornings' })).toBeInTheDocument();
    expect(listRelatedBooks).toHaveBeenCalledWith(7, expect.anything());
    await userEvent.click(within(related).getByRole('link', { name: 'Quiet Mornings' }));
    expect(screen.getByTestId('url')).toHaveTextContent('/books/12');
  });

  it('shows an empty state in Related Reads when there are no related books', async () => {
    listRelatedBooks.mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText('There are no related books yet.')).toBeInTheDocument();
  });

  it('keeps the book visible when Related Reads fails, and retries only that list', async () => {
    listRelatedBooks.mockRejectedValueOnce({ status: 500, code: 'INTERNAL_ERROR' });
    renderPage();

    expect(await screen.findByText('The related books could not be loaded.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'The Habit Loop' })).toBeInTheDocument();
    await userEvent.click(within(screen.getByRole('complementary', { name: 'Related Reads' })).getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('heading', { name: 'Quiet Mornings' })).toBeInTheDocument();
    expect(getBook).toHaveBeenCalledTimes(1);
  });

  it('shows a not-found state with a way back when the book does not exist (FR-DET-09)', async () => {
    getBook.mockRejectedValue({ status: 404, code: 'BOOK_NOT_FOUND', message: 'This book was not found.' });
    renderPage('/books/9999');

    expect(await screen.findByRole('heading', { name: 'Book not found' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to the catalogue' })).toHaveAttribute('href', '/');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(listRelatedBooks).not.toHaveBeenCalled();
  });

  it.each(['abc', '0', '-3', '1.5', '99999999999999999999'])('treats the id "%s" as not found without calling the server', async (id) => {
    renderPage(`/books/${id}`);

    expect(await screen.findByRole('heading', { name: 'Book not found' })).toBeInTheDocument();
    expect(getBook).not.toHaveBeenCalled();
    expect(listRelatedBooks).not.toHaveBeenCalled();
  });

  it('shows an error with Retry for any other failure, and Retry loads the book', async () => {
    getBook.mockRejectedValueOnce({ status: 500, code: 'INTERNAL_ERROR' });
    renderPage();

    expect(await screen.findByText('The book could not be loaded.')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Book not found' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'The Habit Loop' })).toBeInTheDocument();
  });

  it('shows a loading indicator first', async () => {
    getBook.mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(screen.getByText('Loading book')).toBeInTheDocument();
  });

  it('renders text from the server as text, never as markup', async () => {
    getBook.mockResolvedValue({ ...bookDetail, title: '<img src=x onerror=alert(1)>' });
    renderPage();

    expect(await screen.findByRole('heading', { level: 1, name: '<img src=x onerror=alert(1)>' })).toBeInTheDocument();
    expect(document.querySelectorAll('img[src="x"]')).toHaveLength(0);
  });

  it('adds the book to the cart (FR-BSK-01) and has no copies-sold line (CL-04)', async () => {
    const addItem = vi.fn().mockResolvedValue(undefined);
    listRelatedBooks.mockResolvedValue([]);
    renderPage('/books/7', { addItem });
    await screen.findByRole('heading', { level: 1, name: 'The Habit Loop' });

    await userEvent.click(screen.getByRole('button', { name: 'Add to Cart' }));
    expect(addItem).toHaveBeenCalledWith(7, 1);
    expect(screen.queryByText(/copies sold/i)).not.toBeInTheDocument();
  });

  it('adds and removes the book from the wishlist with "Add to Wishlist" / "Remove from Wishlist" (localStorage only)', async () => {
    listRelatedBooks.mockResolvedValue([]);
    renderPage();
    await screen.findByRole('heading', { level: 1, name: 'The Habit Loop' });

    await userEvent.click(screen.getByRole('button', { name: 'Add to Wishlist' }));
    expect(screen.getByRole('button', { name: 'Remove from Wishlist' })).toBeInTheDocument();
    expect(JSON.parse(window.localStorage.getItem('bookygeek.wishlist'))).toEqual([7]);

    await userEvent.click(screen.getByRole('button', { name: 'Remove from Wishlist' }));
    expect(screen.getByRole('button', { name: 'Add to Wishlist' })).toBeInTheDocument();
    expect(JSON.parse(window.localStorage.getItem('bookygeek.wishlist'))).toEqual([]);
  });

  it('follows and unfollows the writer from "About the writer" (localStorage only)', async () => {
    listRelatedBooks.mockResolvedValue([]);
    renderPage();
    const writer = within(await screen.findByRole('region', { name: 'About the writer' }));

    await userEvent.click(writer.getByRole('button', { name: 'Follow' }));
    expect(writer.getByRole('button', { name: 'Following' })).toBeInTheDocument();
    expect(JSON.parse(window.localStorage.getItem('bookygeek.followedWriters'))).toEqual([bookDetail.author]);

    await userEvent.click(writer.getByRole('button', { name: 'Following' }));
    expect(writer.getByRole('button', { name: 'Follow' })).toBeInTheDocument();
  });

  it('shows no cart control for a physical book with no stock', async () => {
    getBook.mockResolvedValue(outOfStockDetail);
    listRelatedBooks.mockResolvedValue([]);
    listBooks.mockResolvedValue({ ...bookPage, items: [] });
    renderPage('/books/31');
    await screen.findByRole('heading', { level: 1, name: 'Out of Print Atlas' });
    expect(screen.queryByRole('button', { name: 'Add to Cart' })).not.toBeInTheDocument();
    expect(screen.getByText('Unavailable')).toBeInTheDocument();
  });

  it('shows the average rating as stars with the review count near the price, and the reviews (AMD-002)', async () => {
    listBookReviews.mockResolvedValue({
      averageRating: 4.5,
      reviewCount: 2,
      reviews: [
        { id: 2, reviewerName: 'Asha Verma', rating: 5, comment: 'Great read', createdAt: '2026-11-18T06:30:00Z' },
        { id: 1, reviewerName: 'Ravi Nair', rating: 4, comment: null, createdAt: '2026-11-17T06:30:00Z' },
      ],
    });
    renderPage();

    expect(await screen.findByText('(2 reviews)')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Average rating 4.5 out of 5' })).toBeInTheDocument();
    const reviews = within(screen.getByRole('region', { name: 'Reviews' }));
    expect(reviews.getByText('Asha Verma')).toBeInTheDocument();
    expect(reviews.getByText('Great read')).toBeInTheDocument();
    expect(reviews.getByText('Ravi Nair')).toBeInTheDocument();
  });

  it('shows "No reviews yet." and a log-in link for a guest', async () => {
    renderPage();
    const reviews = within(await screen.findByRole('region', { name: 'Reviews' }));
    expect(await reviews.findByText('No reviews yet.')).toBeInTheDocument();
    expect(reviews.getByRole('link', { name: 'Log in to write a review' })).toHaveAttribute(
      'href',
      '/login?returnTo=%2Fbooks%2F7',
    );
    expect(reviews.queryByRole('button', { name: 'Submit' })).not.toBeInTheDocument();
  });

  it('shows an error with Retry when the reviews cannot be loaded', async () => {
    listBookReviews.mockRejectedValueOnce({ status: 500, code: 'INTERNAL_ERROR' });
    renderPage();
    const reviews = within(await screen.findByRole('region', { name: 'Reviews' }));
    expect(await reviews.findByText('The reviews could not be loaded.')).toBeInTheDocument();
    await userEvent.click(reviews.getByRole('button', { name: 'Retry' }));
    expect(await reviews.findByText('No reviews yet.')).toBeInTheDocument();
  });

  it('lets a signed-in customer submit a review, reloads the list and clears the form', async () => {
    createBookReview.mockResolvedValue({ id: 1 });
    renderPage('/books/7', {}, { id: 1, fullName: 'Asha Verma', email: 'asha.verma@example.com' });
    const reviews = within(await screen.findByRole('region', { name: 'Reviews' }));

    await userEvent.click(reviews.getByRole('button', { name: 'Submit' }));
    expect(await reviews.findByText('Select a rating from 1 to 5.')).toBeInTheDocument();
    expect(createBookReview).not.toHaveBeenCalled();

    await userEvent.click(reviews.getByRole('radio', { name: '4 stars' }));
    await userEvent.type(reviews.getByLabelText('Comment (optional)'), 'Great read');
    expect(reviews.getByText('10/100')).toBeInTheDocument();
    await userEvent.click(reviews.getByRole('button', { name: 'Submit' }));

    expect(createBookReview).toHaveBeenCalledWith(7, { rating: 4, comment: 'Great read' });
    await vi.waitFor(() => expect(listBookReviews).toHaveBeenCalledTimes(2));
    expect(reviews.getByLabelText('Comment (optional)')).toHaveValue('');
    expect(reviews.getByText('0/100')).toBeInTheDocument();
  });

  it('shows the server message when the book was already reviewed', async () => {
    createBookReview.mockRejectedValue({ status: 409, code: 'ALREADY_REVIEWED', message: 'You have already reviewed this book.' });
    renderPage('/books/7', {}, { id: 1, fullName: 'Asha Verma', email: 'asha.verma@example.com' });
    const reviews = within(await screen.findByRole('region', { name: 'Reviews' }));

    await userEvent.click(reviews.getByRole('radio', { name: '5 stars' }));
    await userEvent.click(reviews.getByRole('button', { name: 'Submit' }));
    expect(await reviews.findByText('You have already reviewed this book.')).toBeInTheDocument();
  });

  it('shows About the writer with the author name and the author\'s other books, without this book (AMD-002)', async () => {
    listBooks.mockResolvedValue({ ...bookPage, items: [paperback, ebook] });
    renderPage();
    const writer = within(await screen.findByRole('region', { name: 'About the writer' }));

    expect(await writer.findByRole('heading', { name: 'Quiet Mornings' })).toBeInTheDocument();
    expect(writer.queryByRole('heading', { name: 'The Habit Loop' })).not.toBeInTheDocument();
    expect(listBooks).toHaveBeenCalledWith({ author: 'Meera Iyer' }, expect.anything());
  });

  it('shows the writer\'s biography when the book detail carries one (AMD-003)', async () => {
    getBook.mockResolvedValue({ ...bookDetail, authorBio: 'Meera Iyer writes about habits.' });
    renderPage();
    const writer = within(await screen.findByRole('region', { name: 'About the writer' }));
    expect(writer.getByText('Meera Iyer writes about habits.')).toBeInTheDocument();
  });

  it('shows only the author name when the writer has no other books', async () => {
    renderPage();
    const writer = within(await screen.findByRole('region', { name: 'About the writer' }));
    expect(writer.getByText('Meera Iyer')).toBeInTheDocument();
    await vi.waitFor(() => expect(writer.queryByRole('status')).not.toBeInTheDocument());
    expect(writer.queryByRole('list')).not.toBeInTheDocument();
  });
});