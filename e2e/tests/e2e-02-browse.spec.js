import { expect, test } from '@playwright/test';

/**
 * E2E-02 (quickstart): a guest opens the landing page and browses the catalogue - category, brand, search,
 * Language / Format / Price filters, sort and paging (12 per page). No account is needed, so the test reads the
 * seeded catalogue (60 books; Self Help holds 14) and changes nothing. Runs against E2E_BASE_URL.
 */

const field = (page, name) => page.locator('label.filter-bar__field', { hasText: new RegExp('^' + name) }).locator('select');
const cards = (page) => page.locator('article.book-card');
const categoryNav = (page) => page.getByRole('navigation', { name: 'Categories' });
const brandNav = (page) => page.getByRole('navigation', { name: 'Brands' });
const countText = (page) => page.getByRole('status').filter({ hasText: /books?$/ });

async function visiblePrices(page) {
  const texts = await page.locator('.book-card__price').allTextContents();
  return texts.map((text) => Number(text.replace(/[^0-9.]/g, '')));
}

/** True when the prices are in order: direction 1 = ascending, -1 = descending. */
function isSorted(prices, direction) {
  return prices.every((price, index) => index === 0 || (price - prices[index - 1]) * direction >= 0);
}

async function totalBooks(page) {
  const text = await countText(page).innerText();
  return Number.parseInt(text, 10);
}

test('E2E-02 a guest browses the catalogue by category, brand, search, filters, sort and page', async ({ page }) => {
  // Landing page: a guest sees the catalogue, the sidebar lists and a Log in link; 12 books are on the first page.
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Catalogue' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Log in' })).toBeVisible();
  await expect(categoryNav(page).getByRole('button', { name: 'All' })).toHaveAttribute('aria-current', 'true');
  await expect(categoryNav(page).getByRole('button', { name: 'Self Help' })).toBeVisible();
  await expect(brandNav(page).getByRole('button', { name: /Maple Leaf Press/ })).toBeVisible();
  await expect(cards(page)).toHaveCount(12);
  await expect(countText(page)).toHaveText('60 books');
  await expect(page.getByText('Page 1 of 5')).toBeVisible();

  // Paging keeps the position in the URL and Previous returns to the first page.
  await page.getByRole('button', { name: 'Next' }).click();
  await expect(page).toHaveURL(/[?&]page=1(&|$)/);
  await expect(page.getByText('Page 2 of 5')).toBeVisible();
  await expect(cards(page)).toHaveCount(12);
  await page.getByRole('button', { name: 'Previous' }).click();
  await expect(page.getByText('Page 1 of 5')).toBeVisible();
  await expect(page).not.toHaveURL(/page=/);

  // Category: Self Help has 14 books, so the second page holds the remaining 2.
  await categoryNav(page).getByRole('button', { name: 'Self Help' }).click();
  await expect(page).toHaveURL(/[?&]category=14(&|$)/);
  await expect(categoryNav(page).getByRole('button', { name: 'Self Help' })).toHaveAttribute('aria-current', 'true');
  await expect(countText(page)).toHaveText('14 books');
  await expect(cards(page)).toHaveCount(12);
  await page.getByRole('button', { name: 'Next' }).click();
  await expect(page.getByText('Page 2 of 2')).toBeVisible();
  await expect(cards(page)).toHaveCount(2);
  await expect(page.getByRole('button', { name: 'Next' })).toBeDisabled();

  // Brand: narrows Self Help to one publisher and keeps the category; the first page is shown again.
  await brandNav(page).getByRole('button', { name: /Maple Leaf Press/ }).click();
  await expect(page).toHaveURL(/brand=Maple(\+|%20)Leaf(\+|%20)Press/);
  await expect(page).toHaveURL(/category=14/);
  await expect(page).not.toHaveURL(/page=/);
  await expect(page.getByRole('heading', { name: 'The Habit Ladder' })).toBeVisible();
  await expect.poll(() => totalBooks(page)).toBeLessThan(14);
  await brandNav(page).getByRole('button', { name: 'All brands' }).click();
  await expect(page).not.toHaveURL(/brand=/);
  await expect(countText(page)).toHaveText('14 books');

  // Search by title: matches are kept inside the selected category.
  await page.getByRole('searchbox', { name: 'Search the catalogue' }).fill('habit');
  await page.getByRole('button', { name: 'Find books' }).click();
  await expect(page).toHaveURL(/q=habit/);
  await expect(page.getByRole('heading', { name: 'The Habit Ladder' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Habits of the Hopeful' })).toBeVisible();
  await expect.poll(() => totalBooks(page)).toBeLessThan(14);

  // Clear filters returns to the whole catalogue.
  await page.getByRole('button', { name: 'Clear filters' }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(countText(page)).toHaveText('60 books');

  // Language, Format and Price filters each default to All and combine.
  await expect(field(page, 'Language')).toHaveValue('');
  await expect(field(page, 'Format')).toHaveValue('');
  await expect(field(page, 'Price')).toHaveValue('');
  await field(page, 'Language').selectOption('Hindi');
  await expect(page).toHaveURL(/language=Hindi/);
  await expect.poll(() => totalBooks(page)).toBeLessThan(60);
  const hindi = await totalBooks(page);
  expect(hindi).toBeGreaterThan(0);
  expect(hindi).toBeLessThan(60);
  await field(page, 'Format').selectOption('PAPERBACK');
  await expect(page).toHaveURL(/format=PAPERBACK/);
  await expect(page).toHaveURL(/language=Hindi/);
  await expect(page.getByRole('heading', { name: 'Mann Ki Shanti' })).toBeVisible();
  await field(page, 'Price').selectOption({ label: 'Under ₹200' });
  await expect(page).toHaveURL(/maxPrice=199\.99/);
  await expect.poll(async () => Math.max(...(await visiblePrices(page)))).toBeLessThan(200);
  await page.getByRole('button', { name: 'Clear filters' }).click();
  await expect(countText(page)).toHaveText('60 books');

  // Sort: price ascending and descending, and the first book on the page changes with it.
  await field(page, 'Sort by').selectOption('PRICE_ASC');
  await expect(page).toHaveURL(/sort=PRICE_ASC/);
  await expect(field(page, 'Sort by')).toHaveValue('PRICE_ASC');
  await expect.poll(async () => (await visiblePrices(page))[0]).toBe(99);
  await expect.poll(async () => isSorted(await visiblePrices(page), 1)).toBe(true);
  await field(page, 'Sort by').selectOption('PRICE_DESC');
  await expect.poll(async () => (await visiblePrices(page))[0]).toBe(1299);
  await expect.poll(async () => isSorted(await visiblePrices(page), -1)).toBe(true);

  // The view survives a reload because every choice is in the URL.
  await page.reload();
  await expect(field(page, 'Sort by')).toHaveValue('PRICE_DESC');
  await expect.poll(async () => (await visiblePrices(page))[0]).toBe(1299);

  // A search with no match shows the empty state, and Clear filters brings the catalogue back.
  await page.getByRole('searchbox', { name: 'Search the catalogue' }).fill('zzzzqqqq');
  await page.getByRole('button', { name: 'Find books' }).click();
  await expect(page.getByRole('heading', { name: 'No books found' })).toBeVisible();
  await expect(cards(page)).toHaveCount(0);
  await page.getByRole('button', { name: 'Clear filters' }).first().click();
  await expect(cards(page)).toHaveCount(12);
  await expect(countText(page)).toHaveText('60 books');
});

test('E2E-02 the book card opens the book page address and the page needs no sign-in', async ({ page }) => {
  await page.goto('/');
  const first = cards(page).first();
  await expect(first.getByRole('link').first()).toHaveAttribute('href', /^\/books\/\d+$/);
  await expect(page.getByRole('group', { name: 'Account' })).toHaveCount(0);
});
