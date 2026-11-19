import { expect, test } from '@playwright/test';
import { PASSWORD } from '../credentials.js';

/**
 * E2E-09 (AMD-002): a guest adds a book with Add to Cart, changes the quantity with plus and minus and opens
 * Go to Cart -> logs in -> opens My Profile (name, e-mail, gift points, saved addresses) -> writes a review on a book
 * page and sees it listed with the average -> the About the writer section is shown.
 * Each run registers its own customer. Runs against E2E_BASE_URL.
 *
 * Seed facts used (AMD-003): book 1 "The Habit Ladder" by Meera Iyer (paperback, 25 in stock). Every writer has five
 * books and a 50-word biography, so the writer section shows the bio and four other books.
 */

async function register(request) {
  const stamp = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const email = `review.${stamp}@example.com`;
  const password = PASSWORD;
  const response = await request.post('/api/v1/auth/register', {
    data: { fullName: 'Asha Verma', email, password, confirmPassword: password },
  });
  expect(response.status()).toBe(201);
  return { email, password, stamp };
}

test('E2E-09 cart control, profile page, review and About the writer', async ({ page, request }) => {
  const account = await register(request);

  // Cart control for a guest: Add to Cart -> plus -> minus -> Go to Cart.
  await page.goto('/books/1');
  const control = page.getByRole('article').first();
  await control.getByRole('button', { name: 'Add to Cart' }).click();
  await expect(control.getByRole('button', { name: 'Increase quantity' })).toBeVisible();
  await expect(control.getByText('1', { exact: true })).toBeVisible();
  await control.getByRole('button', { name: 'Increase quantity' }).click();
  await expect(control.getByText('2', { exact: true })).toBeVisible();
  await control.getByRole('button', { name: 'Decrease quantity' }).click();
  await expect(control.getByText('1', { exact: true })).toBeVisible();
  await control.getByRole('link', { name: 'Go to Cart' }).click();
  await expect(page).toHaveURL(/\/basket$/);
  await expect(page.getByRole('heading', { name: 'Basket' })).toBeVisible();

  // The profile page is for customers: a guest is sent to log in and comes back.
  await page.goto('/profile');
  await expect(page).toHaveURL(/\/login\?returnTo=%2Fprofile/);
  await page.getByLabel('E-mail').fill(account.email);
  await page.getByLabel('Password').fill(account.password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).toHaveURL(/\/profile$/);

  await expect(page.getByRole('heading', { level: 1, name: 'My Profile' })).toBeVisible();
  const details = page.getByRole('region', { name: 'Personal Details' });
  await expect(details.getByText('Asha Verma')).toBeVisible();
  await expect(details.getByText(account.email)).toBeVisible();
  await expect(page.getByRole('region', { name: 'Gift Points' }).getByText('1000 pts')).toBeVisible();
  await expect(page.getByRole('region', { name: 'Saved Addresses' }).getByText('No saved addresses')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Asha Verma' })).toHaveAttribute('href', '/profile');

  // A review on a book page: stars and a comment, then it is listed and the form is cleared.
  await page.goto('/books/1');
  const reviews = page.getByRole('region', { name: 'Reviews' });
  await expect(reviews.getByText(/^\* required$/)).toBeVisible();
  await reviews.getByRole('button', { name: 'Submit' }).click();
  await expect(reviews.getByText('Select a rating from 1 to 5.')).toBeVisible();

  const comment = `Great read ${account.stamp}`.slice(0, 100);
  await reviews.getByRole('radio', { name: '5 stars' }).check({ force: true });
  await reviews.getByLabel('Comment (optional)').fill(comment);
  await expect(reviews.getByText(`${comment.length}/100`)).toBeVisible();
  await reviews.getByRole('button', { name: 'Submit' }).click();

  await expect(reviews.getByText(comment)).toBeVisible();
  await expect(reviews.getByText('Asha Verma').first()).toBeVisible(); // newest first; earlier runs leave same-name reviews
  await expect(reviews.getByLabel('Comment (optional)')).toHaveValue('');
  await expect(page.getByText(/^\(\d+ reviews?\)$/)).toBeVisible();

  // One review per book: a second one shows the server message.
  await reviews.getByRole('radio', { name: '1 star' }).check({ force: true });
  await reviews.getByRole('button', { name: 'Submit' }).click();
  await expect(reviews.getByText('You have already reviewed this book.')).toBeVisible();

  // About the writer: the name, the biography and a row of the four other books, without this one.
  const writer = page.getByRole('region', { name: 'About the writer' });
  await expect(writer.locator('.writer__name')).toHaveText('Meera Iyer');
  await expect(writer.getByText(/Bengaluru habit coach/)).toBeVisible();
  await expect(writer.getByRole('heading', { name: 'The Habit Ladder' })).toHaveCount(0);
  await expect(writer.getByRole('listitem')).toHaveCount(4);
});

test('E2E-09 a guest sees the reviews and a link to log in to write one', async ({ page }) => {
  await page.goto('/books/1');
  const reviews = page.getByRole('region', { name: 'Reviews' });
  await expect(reviews.getByRole('link', { name: 'Log in to write a review' })).toHaveAttribute(
    'href',
    '/login?returnTo=%2Fbooks%2F1',
  );
  await expect(reviews.getByRole('button', { name: 'Submit' })).toHaveCount(0);
});
