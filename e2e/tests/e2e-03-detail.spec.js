import { expect, test } from '@playwright/test';

/**
 * E2E-03 (quickstart): a guest opens a book from the catalogue and checks the details, the delivery date
 * (physical = today + 5 days, eBook = today), the breadcrumb and Related Reads (same category, never the book itself).
 * Read-only against the seeded catalogue (60 books), so no account is needed. Runs against E2E_BASE_URL.
 *
 * Seed facts used: book 1 "The Habit Ladder" (paperback, Self Help, 25 in stock), book 13 "The Two-Minute Reset"
 * (eBook, Self Help), book 5 "Morning Pages Reloaded" (physical, stock 0), book 39 (the only Drama book).
 */

const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** "2026-11-17" -> "Tue, 17 Nov", the form the screens use. */
function label(iso) {
  const [year, month, day] = iso.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return `${DAYS[date.getUTCDay()]}, ${day} ${MONTHS[date.getUTCMonth()]}`;
}

const daysBetween = (from, to) => Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000);

const breadcrumb = (page) => page.getByRole('navigation', { name: 'Breadcrumb' });
const details = (page) => page.getByRole('article').first();
const relatedReads = (page) => page.getByRole('complementary', { name: 'Related Reads' });

test('E2E-03 a guest opens a book from the catalogue and sees its details, delivery date, breadcrumb and Related Reads', async ({
  page,
  request,
}) => {
  // Open the book from its card on the catalogue.
  await page.goto('/?q=Habit%20Ladder');
  await page.getByRole('link', { name: 'The Habit Ladder' }).click();
  await expect(page).toHaveURL(/\/books\/1$/);

  // Details (FR-DET-01): covers, title, author, publisher, format, language, genres, price.
  await expect(page.getByRole('heading', { level: 1, name: 'The Habit Ladder' })).toBeVisible();
  await expect(page.getByRole('img', { name: 'Front cover of The Habit Ladder' })).toBeVisible();
  await expect(page.getByRole('img', { name: 'Back cover of The Habit Ladder' })).toBeVisible();
  await expect(details(page).getByRole('link', { name: 'Meera Iyer' })).toBeVisible();
  await expect(page.getByText('Published by: Maple Leaf Press')).toBeVisible();
  await expect(details(page).getByText('Paperback')).toBeVisible();
  await expect(details(page).getByText('English', { exact: true })).toBeVisible();
  await expect(details(page).getByText('₹499.00')).toBeVisible();
  await expect(details(page).getByText('In stock')).toBeVisible();
  await expect(page.getByRole('list', { name: 'Genres' }).getByRole('link', { name: 'Self Help' })).toBeVisible();

  // Delivery date (FR-DET-02): shown as the API's date, and a physical book is 5 days after an eBook (today).
  const physical = await (await request.get('/api/v1/books/1')).json();
  const ebook = await (await request.get('/api/v1/books/13')).json();
  expect(daysBetween(ebook.deliveryDate, physical.deliveryDate)).toBe(5);
  await expect(page.getByText(`Delivery by ${label(physical.deliveryDate)}`)).toBeVisible();

  // No excluded element (CL-04); the buttons in the details are Add to Cart (E2E-04 covers using it) and the
  // frontend-only "Add to Wishlist". Reviews, ratings and the writer section are allowed since AMD-002.
  await expect(details(page).getByRole('button')).toHaveText(['Add to Cart', 'Add to Wishlist']);
  await expect(page.getByText(/copies sold/i)).toHaveCount(0);

  // Breadcrumb (FR-DET-06): Home / Non-Fiction / Self Help / title; the last level is the book's category.
  const crumbs = breadcrumb(page).getByRole('link');
  await expect(crumbs).toHaveText(['Home', 'Non-Fiction', 'Self Help']);
  await expect(breadcrumb(page).getByText('The Habit Ladder')).toHaveAttribute('aria-current', 'page');

  // Related Reads (FR-DET-04): same category, never the book itself, each entry shows cover, title, author, price.
  const related = relatedReads(page);
  await expect(related.getByRole('heading', { name: 'Related Reads' })).toBeVisible();
  const relatedBooks = await (await request.get('/api/v1/books/1/related')).json();
  expect(relatedBooks.length).toBeGreaterThan(0);
  expect(relatedBooks.map((b) => b.id)).not.toContain(1);
  for (const b of relatedBooks) expect(b.categories.map((c) => c.id)).toContain(14);
  await expect(related.locator('article')).toHaveCount(relatedBooks.length);
  const first = related.locator('article').first();
  await expect(first.locator('img')).toBeVisible();
  await expect(first.getByRole('heading')).toBeVisible();
  await expect(related.getByRole('heading', { name: 'The Habit Ladder' })).toHaveCount(0);

  // Selecting a related book opens its own page (FR-DET-05).
  const target = relatedBooks[0];
  await related.getByRole('link', { name: target.title, exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/books/${target.id}$`));
  await expect(page.getByRole('heading', { level: 1, name: target.title })).toBeVisible();
  await expect(related.getByRole('heading', { name: target.title, exact: true })).toHaveCount(0);

  // Breadcrumb links open the catalogue at that level (CL-25): the parent category lists its sub-categories' books.
  await breadcrumb(page).getByRole('link', { name: 'Non-Fiction' }).click();
  await expect(page).toHaveURL(/[?&]category=2(&|$)/);
  await expect(page.getByRole('navigation', { name: 'Categories' }).getByRole('button', { name: 'Non-Fiction' })).toHaveAttribute(
    'aria-current',
    'true',
  );
});

test('E2E-03 an eBook is available, delivered today, and shows no stock', async ({ page, request }) => {
  const ebook = await (await request.get('/api/v1/books/13')).json();
  expect(ebook.stockQuantity).toBeNull();

  await page.goto('/books/13');
  await expect(page.getByRole('heading', { level: 1, name: 'The Two-Minute Reset' })).toBeVisible();
  await expect(details(page).getByText('eBook', { exact: true })).toBeVisible();
  await expect(details(page).getByText('Available', { exact: true })).toBeVisible();
  await expect(page.getByText('Unavailable')).toHaveCount(0);
  await expect(page.getByText(`Delivery by ${label(ebook.deliveryDate)}`)).toBeVisible();
});

test('E2E-03 a physical book with no stock is Unavailable but keeps its delivery date', async ({ page, request }) => {
  const book = await (await request.get('/api/v1/books/5')).json();
  expect(book.stockQuantity).toBe(0);

  await page.goto('/books/5');
  await expect(page.getByRole('heading', { level: 1, name: 'Morning Pages Reloaded' })).toBeVisible();
  await expect(details(page).getByText('Unavailable', { exact: true })).toBeVisible();
  await expect(page.getByText('In stock')).toHaveCount(0);
  await expect(page.getByText(`Delivery by ${label(book.deliveryDate)}`)).toBeVisible();
});

test('E2E-03 a book with no related books shows an empty state', async ({ page }) => {
  await page.goto('/books/39');
  await expect(page.getByRole('heading', { level: 1, name: 'Three Doors, One Stage' })).toBeVisible();
  await expect(relatedReads(page).getByText('There are no related books yet.')).toBeVisible();
  await expect(relatedReads(page).locator('article')).toHaveCount(0);
});

test('E2E-03 an unknown book shows a not-found page with a way back', async ({ page }) => {
  await page.goto('/books/9999');
  await expect(page.getByRole('heading', { name: 'Book not found' })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
  await page.getByRole('link', { name: 'Back to the catalogue' }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole('heading', { name: 'Catalogue' })).toBeVisible();

  await page.goto('/books/not-a-number');
  await expect(page.getByRole('heading', { name: 'Book not found' })).toBeVisible();
});

test('E2E-03 an author link on a book page opens the catalogue filtered by that author', async ({ page }) => {
  await page.goto('/books/1');
  await details(page).getByRole('link', { name: 'Meera Iyer' }).click();
  await expect(page).toHaveURL(/[?&]author=Meera\+Iyer/);
  await expect(page.getByText('Author:')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'The Habit Ladder' })).toBeVisible();
});
